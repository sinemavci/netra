package com.netra.library

object OfflineQueueManager {
    private val queue = mutableListOf<() -> Unit>()

    fun push(request: () -> Unit) {
        queue.add(request)
        // TODO: Save to Room/Database here
        //Log.e("Netra", "Request queued: ${request.url}")
    }

    fun remove(id: String) {
        // queue.removeAll { it.id == id }
    }

    fun processQueue() {
        //todo: check call is cancelled before?
        if (queue.isEmpty()) return
        queue.forEach {
            it.invoke()
        }
        queue.clear()
    }
}