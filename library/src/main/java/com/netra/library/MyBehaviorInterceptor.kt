package com.netra.library

import okhttp3.*

class MyBehaviorInterceptor(private val listener: SdkStatusListener): Interceptor {
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

        while (!response.isSuccessful && attempt < maxRetries && shouldRetry(response)) {
            println("Response failed. Attempt $attempt of $maxRetries")
            attempt++
            listener.attempt(attempt)
            response.close()
            response = chain.proceed(request)
        }

        return response
    }
}