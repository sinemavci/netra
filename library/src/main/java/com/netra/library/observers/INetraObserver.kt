package com.netra.library.observers

interface INetraObserver {
    fun onNetworkChanged(event: NetworkEvent)

    fun onCacheChanged(event: CacheEvent)

    fun onQueueChanged(event: RequestQueuedEvent)

    fun onRequestExecuted(event: RequestEvent)

    fun onResponseReceived(event: ResponseEvent)
}