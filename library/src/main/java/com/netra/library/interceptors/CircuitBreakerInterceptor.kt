package com.netra.library.interceptors

import android.util.Log
import com.netra.library.NetraClient
import com.netra.library.NetraRequest
import com.netra.library.managers.ObserverManager
import com.netra.library.observers.RequestEvent
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class CircuitBreakerInterceptor(failureThreshold: Int? = 5, val retryDelayMs: Long? = 1000L) : Interceptor {
    private val maxRetries = failureThreshold ?: 5

    private fun shouldRetry(response: Response): Boolean {
        val code = response.code
        // Retry on Server Errors (500-599) or Timeout (408)
        return code in 500..599 || code == 408
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val netraRequest = request.tag(NetraRequest::class.java)
        if (netraRequest != null) {
            ObserverManager.notifyRequestEvent(
                RequestEvent.RequestExecuted(
                    request = netraRequest
                )
            )

            if (netraRequest.config.globalFailureCount.get() >= maxRetries) {
                val timeSinceLastFailure =
                    System.currentTimeMillis() - netraRequest.config.lastFailureTime
                if (timeSinceLastFailure < 30000) {
                    throw IOException("Circuit is OPEN: Server is unstable. Try again later.")
                } else {
                    netraRequest.config.globalFailureCount.set(0)
                }
            }
        }

        val isSlow = request.header("X-Priority") == "Slow"
        val timeout = if (isSlow) 60L else 10L

        val currentChain = chain
            .withConnectTimeout(timeout.toInt(), TimeUnit.SECONDS)
            .withReadTimeout(timeout.toInt(), TimeUnit.SECONDS)

        var attempt = 1
        var response: Response = currentChain.proceed(request)
        Log.e("response", "response in interceptor: ${response.code}")

        while (!response.isSuccessful && shouldRetry(response) && attempt < maxRetries && !chain.call()
                .isCanceled()
        ) {

            println("Response failed (Code: ${response.code}). Attempt $attempt of $maxRetries")
            response.close()

            attempt++
            //reporter?.onStatusUpdate(Status.Retrying(response.code, attempt))

            Thread.sleep((retryDelayMs ?: 1000L) * attempt.toLong())
            response = currentChain.proceed(request)
        }

        if (shouldRetry(response)) {
            netraRequest?.config?.globalFailureCount?.incrementAndGet()
            netraRequest?.config?.lastFailureTime = System.currentTimeMillis()
        } else if (response.isSuccessful) {
            netraRequest?.config?.globalFailureCount?.set(0)
        }

        return response
    }
}