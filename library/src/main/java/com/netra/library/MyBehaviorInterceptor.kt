package com.netra.library

import okhttp3.*
import java.util.concurrent.TimeUnit

class MyBehaviorInterceptor: Interceptor {
    val maxRetries = 3

    private fun shouldRetry(response: Response): Boolean {
        val code = response.code
        // Only retry on server errors (500s) or specific timeouts
        // Do NOT retry on 404, 401 (Unauthorized), or 403 (Forbidden)
        return code in 500..599 || code == 408 // 408 is Request Timeout
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 1
        val reporter = request.tag(StatusReporter::class.java)
        val timeout = if(request.header("X-Priority") === "Slow") 60 else 10

        while (!response.isSuccessful && attempt < maxRetries && shouldRetry(response) && !chain.call().isCanceled()) {
            val updatedChain = chain.withConnectTimeout(timeout, TimeUnit.SECONDS)
            println("Response failed. Attempt $attempt of $maxRetries")
            attempt++
            reporter?.onStatusUpdate(Status.Retrying(attempt))
            response.close()
            response = updatedChain.proceed(request)
        }

        return response
    }
}