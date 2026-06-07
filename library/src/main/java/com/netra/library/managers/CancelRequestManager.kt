package com.netra.library.managers

import com.netra.library.NetraCall
import java.util.concurrent.ConcurrentHashMap

object CancelRequestManager {
    private val activeCalls = ConcurrentHashMap<String, NetraCall>()

    fun getAllRequests(): List<Pair<String, NetraCall>> {
        return activeCalls.toList()
    }

    fun add(id: String, call: NetraCall) {
        activeCalls[id] = call
    }

    fun remove(id: String) {
        activeCalls.remove(id)
    }

    fun cancel(id: String): Boolean {
        val netraCall = activeCalls[id]
        return if (netraCall != null && !netraCall.call.isCanceled()) {
            netraCall.call.cancel()
            activeCalls.remove(id)
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