package com.netra.library.managers

import com.netra.library.observers.CacheEvent
import com.netra.library.observers.INetraObserver
import com.netra.library.observers.NetworkEvent
import com.netra.library.observers.QueueEvent
import com.netra.library.observers.RequestEvent
import com.netra.library.utils.EventDispatcher

internal object ObserverManager {
    val observers = mutableMapOf<String, MutableList<INetraObserver>>()

    fun addObserver(clientId: String, observer: INetraObserver) {
        val list = observers.getOrPut(clientId) { mutableListOf() }
        if (observer !in list) {
            list.add(observer)
        }
    }

    fun removeObserver(observer: INetraObserver) {
        observers.values.forEach { it.remove(observer) }
    }
    fun removeObserver(clientId: String, observer: INetraObserver) {
        observers[clientId]?.remove(observer)
    }
    //todo: notify based client id
    fun notifyNetworkEvent(event: NetworkEvent) {
        EventDispatcher.runOnMain {
            observers.values.toTypedArray().forEach { observers ->
                observers.forEach { observer ->
                    observer.onNetworkChanged(event)
                }
            }
        }
    }

    fun notifyCacheEvent(clientId: String, event: CacheEvent) {
        EventDispatcher.runOnMain {
            observers[clientId]?.toTypedArray()?.forEach { observer ->
                observer.onCacheChanged(event)
            }
        }
    }

    //todo: notify based client id
    fun notifyQueuedEvent(event: QueueEvent) {
        EventDispatcher.runOnMain {
            observers.values.toTypedArray().forEach { observers ->
                observers.forEach { observer ->
                    observer.onQueueChanged(event)
                }
            }
        }
    }

    fun notifyRequestEvent(clientId: String, event: RequestEvent) {
        EventDispatcher.runOnMain {
            observers[clientId]?.toTypedArray()?.forEach { observer ->
                observer.onRequestChanged(event)
            }
        }
    }
}