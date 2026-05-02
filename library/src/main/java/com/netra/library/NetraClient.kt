package com.netra.library

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.collection.LruCache
import com.netra.library.converter.IConverter
import com.netra.library.interceptors.NetraInterceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.util.concurrent.atomic.AtomicInteger

val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
val cacheSize = maxMemory / 8
class NetraClient private constructor(
    val context: Context,
    var baseUrl: String? = null,
    var converter: IConverter? = null,
) {
    data class Builder(
        val context: Context,
        var baseUrl: String? = null,
        var converter: IConverter? = null,
    ) {
        fun baseUrl(url: String): Builder {
            this.baseUrl = url
            return this
        }

        fun addConverterFactory(netraConverter: IConverter?): Builder {
            this.converter = netraConverter
            return this
        }

        fun build(): NetraClient {
            initCompanion(context)

            if (baseUrl != null) {
                return NetraClient(context, baseUrl!!, converter)
            } else {
                throw Exception("Base url not found!")
            }
        }
    }

    fun get(path: String): RequestBuilder {
        return RequestBuilder(context, Command.Get(baseUrl + path), client, converter)
    }

    fun post(path: String, requestBody: RequestBody): RequestBuilder {
        return RequestBuilder(context, Command.Post(baseUrl + path, requestBody), client, converter)
    }

    fun put(path: String, requestBody: RequestBody): RequestBuilder {
        return RequestBuilder(context, Command.Put(baseUrl + path, requestBody), client, converter)
    }

    fun patch(path: String, requestBody: RequestBody): RequestBuilder {
        return RequestBuilder(
            context,
            Command.Patch(baseUrl + path, requestBody),
            client,
            converter
        )
    }

    fun delete(path: String, requestBody: RequestBody? = null): RequestBuilder {
        return RequestBuilder(
            context,
            Command.Delete(baseUrl + path, requestBody),
            client,
            converter
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


        internal fun initCompanion(context: Context) {
            if (!::connectivityManager.isInitialized) {
                connectivityManager = context.applicationContext
                    .getSystemService(ConnectivityManager::class.java)
            }
            if (!::client.isInitialized) {
                client = OkHttpClient().newBuilder().addInterceptor(NetraInterceptor()).build()
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
                    super.onAvailable(network)
                }
            }

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        }
    }
}

class StatusReporter(val onStatusUpdate: (Status) -> Unit)

