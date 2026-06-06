package com.netra.library.managers

import com.netra.library.NetraCall
import java.util.concurrent.ConcurrentHashMap

object CancelRequestManager {
    private val activeCalls = ConcurrentHashMap<String, NetraCall>()

    fun add(url: String, call: NetraCall) {
        activeCalls[url] = call
    }

    fun remove(url: String) {
        activeCalls.remove(url)
    }

    fun cancel(key: String): Boolean {
        val netraCall = activeCalls[key]
        return if (netraCall != null && !netraCall.call.isCanceled()) {
            netraCall.call.cancel()
            activeCalls.remove(key)
            true
        } else {
            false
        }
    }

    fun cancelWhenDestroyed() {
        for (activeCall in activeCalls) {
            if (activeCall.value.isCancelledWhenDestroy) {
                cancel(activeCall.key)
            }
        }
    }
}