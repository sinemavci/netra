package com.netra.library

import android.util.Log
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class MyBehaviorInterceptor : Interceptor {
    private val maxRetries = 5

    private fun shouldRetry(response: Response): Boolean {
        val code = response.code
        // Retry on Server Errors (500-599) or Timeout (408)
        return code in 500..599 || code == 408
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val reporter = request.tag(StatusReporter::class.java)

        Log.e("NetraClient.globalFailureCount:", "NetraClient.globalFailureCount.get(): ${NetraClient.globalFailureCount.get()}")

        if (NetraClient.globalFailureCount.get() >= 5) {
            val timeSinceLastFailure = System.currentTimeMillis() - NetraClient.lastFailureTime
            if (timeSinceLastFailure < 30000) {
                throw IOException("Circuit is OPEN: Server is unstable. Try again later.")
            } else {
                NetraClient.globalFailureCount.set(0)
            }
        }

        val isSlow = request.header("X-Priority") == "Slow"
        val timeout = if (isSlow) 60L else 10L

        val currentChain = chain
            .withConnectTimeout(timeout.toInt(), TimeUnit.SECONDS)
            .withReadTimeout(timeout.toInt(), TimeUnit.SECONDS)

        var attempt = 1
        var response: Response = currentChain.proceed(request)

        while (!response.isSuccessful && shouldRetry(response) && attempt < maxRetries && !chain.call().isCanceled()) {

            println("Response failed (Code: ${response.code}). Attempt $attempt of $maxRetries")
            response.close()

            attempt++
            reporter?.onStatusUpdate(Status.Retrying(response.code, attempt))

            Thread.sleep(1000 * attempt.toLong())
            response = currentChain.proceed(request)
        }

        if (response.code >= 500) {
            NetraClient.globalFailureCount.incrementAndGet()
            NetraClient.lastFailureTime = System.currentTimeMillis()
        } else if (response.isSuccessful) {
            NetraClient.globalFailureCount.set(0)
        }

        return response
    }
}