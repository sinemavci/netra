package com.netra.library

import okhttp3.*

class MyBehaviorInterceptor(private val listener: SdkStatusListener): Interceptor {
    val maxRetries = 3

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 1

        while (!response.isSuccessful && attempt < maxRetries) {
            println("Response failed. Attempt $attempt of $maxRetries")
            attempt++
            listener.attempt(attempt)
            response.close()
            response = chain.proceed(request)
        }

        return response
    }
}