package com.netra.library

import android.app.Application
import android.content.Context
import androidx.collection.LruCache
import com.netra.library.converter.IConverter
import com.netra.library.enums.Command
import com.netra.library.interceptors.BaseInterceptor
import com.netra.library.interceptors.CircuitBreakerInterceptor
import com.netra.library.managers.LifecycleCallbacks
import com.netra.library.managers.OfflineQueueManager
import com.netra.library.managers.ObserverManager
import com.netra.library.observers.INetraObserver
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

    fun addObserver(observer: INetraObserver) {
        if (observer !in ObserverManager.observers) {
            ObserverManager.observers.add(observer)
        }
    }

    fun removeObserver(observer: INetraObserver) {
        ObserverManager.observers.remove(observer)
    }

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
            client = OkHttpClient().newBuilder()
                .addInterceptor(CircuitBreakerInterceptor(failureThreshold, retryDelayMs)).build()
            return this
        }

        fun addConverterFactory(netraConverter: IConverter?): Builder {
            this.converter = netraConverter
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

    fun get(path: String): NetraRequestBuilder {
        return NetraRequestBuilder(context, Command.Get(baseUrl + path), client, converter, headers)
    }

    fun post(path: String, requestBody: NetraRequestBody): NetraRequestBuilder {
        return NetraRequestBuilder(
            context,
            Command.Post(baseUrl + path, requestBody),
            client,
            converter,
            headers
        )
    }

    fun put(path: String, requestBody: NetraRequestBody): NetraRequestBuilder {
        return NetraRequestBuilder(
            context,
            Command.Put(baseUrl + path, requestBody),
            client,
            converter,
            headers
        )
    }

    fun patch(path: String, requestBody: NetraRequestBody): NetraRequestBuilder {
        return NetraRequestBuilder(
            context,
            Command.Patch(baseUrl + path, requestBody),
            client,
            converter,
            headers
        )
    }

    fun delete(path: String, requestBody: NetraRequestBody? = null): NetraRequestBuilder {
        return NetraRequestBuilder(
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
        internal lateinit var client: OkHttpClient
            private set

        internal fun initCompanion(context: Context) {
            if (!::client.isInitialized) {
                client = OkHttpClient().newBuilder().addInterceptor(BaseInterceptor()).build()
            }

            OfflineQueueManager.init(context.applicationContext)

            val application = context.applicationContext as Application
            application.registerActivityLifecycleCallbacks(
                LifecycleCallbacks()
            )
        }
    }
}

class StatusReporter(val onStatusUpdate: (NetraResponse) -> Unit)

