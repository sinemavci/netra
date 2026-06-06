package com.netra.library.interceptors

import android.util.Log
import com.netra.library.NetraRequest
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
                Log.e("", "RetryInterceptor proceed response: ${response.code} ${response.body}")
                //reporter?.onStatusUpdate(Status.Retrying(response.code, attempt))
                return response
            } catch (e: IOException) {
                Log.e("", "RetryInterceptor proceed response failed: ${e}")
                reporter?.onStatusUpdate(NetraRequest.getNetraFailedResponse(e))
                lastException = e
                attempt++
                Thread.sleep(2000L * attempt)
            }
        }
        throw lastException!!
    }
}