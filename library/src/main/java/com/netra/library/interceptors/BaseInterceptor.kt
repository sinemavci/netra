package com.netra.library.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class BaseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val isSlow = request.header("X-Priority") == "Slow"
        val timeout = if (isSlow) 60L else 10L

        val currentChain = chain
            .withConnectTimeout(timeout.toInt(), TimeUnit.SECONDS)
            .withReadTimeout(timeout.toInt(), TimeUnit.SECONDS)
        val response: Response = currentChain.proceed(request)

        return response
    }
}