package com.netra.library

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.collection.LruCache
import com.netra.library.converter.IConverter
import com.netra.library.enums.Command
import com.netra.library.interceptors.BaseInterceptor
import com.netra.library.interceptors.CircuitBreakerInterceptor
import com.netra.library.managers.OfflineQueueManager
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
val cacheSize = maxMemory / 8
class NetraClient private constructor(
    val context: Context,
    var baseUrl: String? = null,
    var converter: IConverter? = null,
    var headers: Map<String, String>,
) {
    var id: String = UUID.randomUUID().mostSignificantBits.toString()
    data class Builder(
        val context: Context,
        var baseUrl: String? = null,
        var converter: IConverter? = null,
        var headers: MutableMap<String, String> = mutableMapOf(),
    ) {
        fun baseUrl(url: String): Builder {
            this.baseUrl = url
            return this
        }

        fun addHeaders(headerParam: Map<String, String>): Builder {
            this.headers.putAll(headerParam)
            return this
        }

        fun circuitBreaker(failureThreshold: Int? = 5, retryDelayMs: Long? = 1000L): Builder {
            client = OkHttpClient().newBuilder().addInterceptor(CircuitBreakerInterceptor(failureThreshold, retryDelayMs)).build()
            return this
        }

        fun addConverterFactory(netraConverter: IConverter?): Builder {
            this.converter = netraConverter
            return this
        }

        fun addObserver(observer: INetraObserver): Builder {
            if (observer !in observers) {
                observers.add(observer)
            }
            return this
        }

        fun removeObserver(observer: INetraObserver): Builder {
            observers.remove(observer)
            return this
        }

        fun build(): NetraClient {
            initCompanion(context)

            if (baseUrl != null) {
                return NetraClient(context, baseUrl!!, converter, headers)
            } else {
                throw Exception("Base url not found!")
            }
        }
    }

    fun get(path: String): RequestBuilder {
        return RequestBuilder(context, Command.Get(baseUrl + path), client, converter, headers)
    }

    fun post(path: String, requestBody: NetraRequestBody): RequestBuilder {
        return RequestBuilder(context, Command.Post(baseUrl + path, requestBody), client, converter, headers)
    }

    fun put(path: String, requestBody: NetraRequestBody): RequestBuilder {
        return RequestBuilder(context, Command.Put(baseUrl + path, requestBody), client, converter, headers)
    }

    fun patch(path: String, requestBody: NetraRequestBody): RequestBuilder {
        return RequestBuilder(
            context,
            Command.Patch(baseUrl + path, requestBody),
            client,
            converter,
            headers
        )
    }

    fun delete(path: String, requestBody: NetraRequestBody? = null): RequestBuilder {
        return RequestBuilder(
            context,
            Command.Delete(baseUrl + path, requestBody),
            client,
            converter,
            headers
        )
    }

    companion object {
        internal val globalFailureCount = AtomicInteger(0)

        internal var lastFailureTime: Long = 0

        internal val memoryCache = object : LruCache<String, ByteArray>(cacheSize) {
            override fun sizeOf(key: String, bitmap: ByteArray): Int {
                return bitmap.size / 1024
            }
        }

        internal lateinit var connectivityManager: ConnectivityManager
            private set

        internal lateinit var client: OkHttpClient
            private set

        internal val observers = mutableListOf<INetraObserver>()

        internal fun notifyRequestEvent(event: NetworkEvent) {
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

        internal fun initCompanion(context: Context) {
            if (!::connectivityManager.isInitialized) {
                connectivityManager = context.applicationContext
                    .getSystemService(ConnectivityManager::class.java)
            }
            if (!::client.isInitialized) {
                client = OkHttpClient().newBuilder().addInterceptor(BaseInterceptor()).build()
            }

            OfflineQueueManager.init(context.applicationContext)
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    OfflineQueueManager.processQueue(client = client)
                    notifyRequestEvent(NetworkEvent.ConnectionRestored)
                    super.onAvailable(network)
                }
            }

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        }
    }
}

class StatusReporter(val onStatusUpdate: (NetraResponse) -> Unit)

