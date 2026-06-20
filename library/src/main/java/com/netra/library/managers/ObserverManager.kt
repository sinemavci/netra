package com.netra.library.managers

import android.util.Log
import com.netra.library.observers.CacheEvent
import com.netra.library.observers.INetraObserver
import com.netra.library.observers.NetworkEvent
import com.netra.library.observers.QueueEvent
import com.netra.library.observers.RequestEvent
import com.netra.library.utils.EventDispatcher

internal object ObserverManager {
    val observers = mutableListOf<INetraObserver>()

    fun notifyNetworkEvent(event: NetworkEvent) {
        EventDispatcher.runOnMain {
            observers.toTypedArray().forEach { observer ->
                try {
                    observer.onNetworkChanged(event)
                } catch (e: Exception) {
                    Log.e("MapRays", "Error in observer: ${e.message}", e)
                }
            }
        }
    }

    fun notifyCacheEvent(event: CacheEvent) {
        EventDispatcher.runOnMain {
            observers.toTypedArray().forEach { observer ->
                try {
                    observer.onCacheChanged(event)
                } catch (e: Exception) {
                    Log.e("MapRays", "Error in observer: ${e.message}", e)
                }
            }
        }
    }

    fun notifyQueuedEvent(event: QueueEvent) {
        EventDispatcher.runOnMain {
            observers.toTypedArray().forEach { observer ->
                try {
                    observer.onQueueChanged(event)
                } catch (e: Exception) {
                    Log.e("MapRays", "Error in observer: ${e.message}", e)
                }
            }
        }
    }

    fun notifyRequestEvent(event: RequestEvent) {
        EventDispatcher.runOnMain {
            observers.toTypedArray().forEach { observer ->
                try {
                    observer.onRequestChanged(event)
                } catch (e: Exception) {
                    Log.e("MapRays", "Error in observer: ${e.message}", e)
                }
            }
        }
    }
}