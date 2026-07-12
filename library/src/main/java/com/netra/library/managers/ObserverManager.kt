package com.netra.library.managers

import com.netra.library.observers.CacheEvent
import com.netra.library.observers.INetraObserver
import com.netra.library.observers.NetworkEvent
import com.netra.library.observers.QueueEvent
import com.netra.library.observers.RequestEvent
import com.netra.library.utils.EventDispatcher

internal object ObserverManager {
    val observers = mutableListOf<INetraObserver>()

    fun addObserver(observer: INetraObserver) {
        if (observer !in observers) {
            observers.add(observer)
        }
    }

    fun removeObserver(observer: INetraObserver) {
        observers.remove(observer)
    }

    fun notifyNetworkEvent(event: NetworkEvent) {
        EventDispatcher.runOnMain {
            observers.toTypedArray().forEach { observer ->
                observer.onNetworkChanged(event)
            }
        }
    }

    fun notifyCacheEvent(event: CacheEvent) {
        EventDispatcher.runOnMain {
            observers.toTypedArray().forEach { observer ->
                observer.onCacheChanged(event)
            }
        }
    }

    fun notifyQueuedEvent(event: QueueEvent) {
        EventDispatcher.runOnMain {
            observers.toTypedArray().forEach { observer ->
                observer.onQueueChanged(event)
            }
        }
    }

    fun notifyRequestEvent(event: RequestEvent) {
        EventDispatcher.runOnMain {
            observers.toTypedArray().forEach { observer ->
                observer.onRequestChanged(event)
            }
        }
    }
}