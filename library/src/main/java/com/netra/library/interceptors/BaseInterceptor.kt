package com.netra.library.interceptors

import android.util.Log
import com.netra.library.NetraRequest
import com.netra.library.managers.ObserverManager
import com.netra.library.observers.RequestEvent
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class BaseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val netraRequest = request.tag(NetraRequest::class.java)
        if (netraRequest != null) {
            ObserverManager.notifyRequestEvent(
                RequestEvent.RequestExecuted(
                    key = request.url.toString(),
                    request = netraRequest
                )
            )
        }

        val isSlow = request.header("X-Priority") == "Slow"
        val timeout = if (isSlow) 60L else 10L

        val currentChain = chain
            .withConnectTimeout(timeout.toInt(), TimeUnit.SECONDS)
            .withReadTimeout(timeout.toInt(), TimeUnit.SECONDS)
        val response: Response = currentChain.proceed(request)

        return response
    }
}