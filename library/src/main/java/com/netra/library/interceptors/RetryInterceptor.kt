package com.netra.library.interceptors

import com.netra.library.enums.Status
import com.netra.library.StatusReporter
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException

class RetryInterceptor(private val maxRetries: Int) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        val request = chain.request()
        var lastException: IOException? = null
        val reporter = request.tag(StatusReporter::class.java)

        while (attempt < maxRetries) {
            try {
                val response = chain.proceed(chain.request())
                reporter?.onStatusUpdate(Status.Retrying(response.code, attempt))
                return response
            } catch (e: IOException) {
                reporter?.onStatusUpdate(Status.Failure(e.message))
                lastException = e
                attempt++
                Thread.sleep(2000L * attempt)
            }
        }
        throw lastException!!
    }
}