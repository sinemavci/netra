package com.netra.library.interceptors

import com.netra.library.NetraResponse
import okhttp3.Request

interface NetraInterceptor {
    fun intercept(chain: NetraChain): NetraResponse.ResponseReceived<*>

    interface NetraChain {
        fun request(): Request
        fun proceed(request: Request): NetraResponse.ResponseReceived<*>
    }
}