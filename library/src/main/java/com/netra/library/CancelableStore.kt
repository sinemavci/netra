package com.netra.library

import okhttp3.Call
import java.util.concurrent.ConcurrentHashMap

object CancelableStore {
    private val activeCalls = ConcurrentHashMap<String, Call>()

    fun getSources(): List<Call> {
        return activeCalls.values.toList()
    }

    fun add(url: String, call: Call) {
        activeCalls[url] = call
    }

    fun remove(url: String) {
        activeCalls.remove(url)
    }

    fun cancel(key: String): Boolean {
        val call = activeCalls[key]
        return if (call != null && !call.isCanceled()) {
            call.cancel()
            activeCalls.remove(key)
            true
        } else {
            false
        }
    }
}