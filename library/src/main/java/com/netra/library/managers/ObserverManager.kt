package com.netra.library.managers

import android.util.Log
import com.netra.library.observers.CacheEvent
import com.netra.library.observers.INetraObserver
import com.netra.library.observers.NetworkEvent
import com.netra.library.observers.RequestEvent
import com.netra.library.observers.RequestQueuedEvent
import com.netra.library.observers.ResponseEvent
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

    fun notifyQueuedEvent(event: RequestQueuedEvent) {
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
                    observer.onRequestExecuted(event)
                } catch (e: Exception) {
                    Log.e("MapRays", "Error in observer: ${e.message}", e)
                }
            }
        }
    }

    fun notifyResponseEvent(event: ResponseEvent) {
        EventDispatcher.runOnMain {
            observers.toTypedArray().forEach { observer ->
                try {
                    observer.onResponseReceived(event)
                } catch (e: Exception) {
                    Log.e("MapRays", "Error in observer: ${e.message}", e)
                }
            }
        }
    }
}