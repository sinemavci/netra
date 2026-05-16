package com.netra.library

interface INetraObserver {
    fun onNetworkChanged(event: NetworkEvent)

    fun onCacheChanged(event: CacheEvent)

    fun onQueueChanged(event: RequestQueuedEvent)
}