package com.netra.library.observers

interface INetraObserver {
    fun onNetworkChanged(event: NetworkEvent)

    fun onCacheChanged(event: CacheEvent)

    fun onRequestChanged(event: RequestEvent)

    fun onQueueChanged(event: QueueEvent)
}