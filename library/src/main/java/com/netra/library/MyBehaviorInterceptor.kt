package com.netra.library

import okhttp3.*

class MyBehaviorInterceptor: Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        println("--- Request Sent! [URL: ${request.url}] ---")

        val response = chain.proceed(request)

        println("--- Response Received! [Code: ${response.code}] ---")

        return response
    }
}