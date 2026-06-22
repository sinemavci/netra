package com.netra.library

import android.app.Application
import android.content.Context
import androidx.collection.LruCache
import com.netra.library.converter.IConverter
import com.netra.library.enums.Command
import com.netra.library.interceptors.BaseInterceptor
import com.netra.library.interceptors.CircuitBreakerInterceptor
import com.netra.library.interceptors.NetraInterceptor
import com.netra.library.managers.CancelRequestManager
import com.netra.library.managers.LifecycleCallbacks
import com.netra.library.managers.MemoryCacheEntry
import com.netra.library.managers.OfflineQueueManager
import com.netra.library.managers.ObserverManager
import com.netra.library.managers.cacheSize
import com.netra.library.observers.INetraObserver
import com.netra.library.observers.RequestEvent
import com.netra.library.utils.ResponseUtil
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class NetraClient private constructor(internal val config: NetraClientConfig) {
    var id: String = UUID.randomUUID().mostSignificantBits.toString()

    var pendingRequests: List<Pair<String, NetraCall>> = CancelRequestManager.getAllRequests()

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

        fun addInterceptor(netraInterceptor: NetraInterceptor): Builder {
            val okHttpInterceptor = object : Interceptor {
                override fun intercept(chain: Interceptor.Chain): Response {
                    val okHttpRequest = chain.request()
                    val netraRequest = okHttpRequest.tag(NetraRequest::class.java)
                    if (netraRequest != null) {
                        ObserverManager.notifyRequestEvent(
                            RequestEvent.RequestExecuted(
                                request = netraRequest
                            )
                        )
                    }

                    val netraChain = object : NetraInterceptor.NetraChain {
                        override fun request(): Request {
                            return okHttpRequest
                        }

                        override fun proceed(request: Request): NetraResponse {
                            val okHttpResponse = chain.proceed(request)
                            return ResponseUtil.okHttpResponseToNetra(okHttpResponse)
                        }
                    }

                    val netraResponse = netraInterceptor.intercept(netraChain)
                    return ResponseUtil.netraResponseToOkHttp(netraResponse, okHttpRequest)
                }
            }
            client = OkHttpClient().newBuilder()
                .addInterceptor(okHttpInterceptor).build()
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
                val config = NetraClientConfig(context, client, baseUrl!!, converter, headers)
                return NetraClient(config)
            } else {
                throw Exception("Base url not found!")
            }
        }
    }

    fun get(path: String): NetraRequestBuilder {
        return NetraRequestBuilder(config, Command.Get(config.baseUrl + path))
    }

    fun post(path: String, requestBody: NetraRequestBody): NetraRequestBuilder {
        return NetraRequestBuilder(
            config,
            Command.Post(config.baseUrl + path, requestBody),
        )
    }

    fun put(path: String, requestBody: NetraRequestBody): NetraRequestBuilder {
        return NetraRequestBuilder(
            config,
            Command.Put(config.baseUrl + path, requestBody),
        )
    }

    fun patch(path: String, requestBody: NetraRequestBody): NetraRequestBuilder {
        return NetraRequestBuilder(
            config,
            Command.Patch(config.baseUrl + path, requestBody),
        )
    }

    fun delete(path: String, requestBody: NetraRequestBody? = null): NetraRequestBuilder {
        return NetraRequestBuilder(
            config,
            Command.Delete(config.baseUrl + path, requestBody),
        )
    }

    companion object {
        internal val globalFailureCount = AtomicInteger(0)

        internal var lastFailureTime: Long = 0

        internal val memoryCache = object : LruCache<String, MemoryCacheEntry>(cacheSize) {
            override fun sizeOf(key: String, value: MemoryCacheEntry): Int {
                return value.data.size / 1024
            }
        }
        internal lateinit var client: OkHttpClient
            private set

        internal fun initCompanion(context: Context) {
            if (!::client.isInitialized) {
                client =
                    OkHttpClient().newBuilder().addInterceptor(BaseInterceptor()).build()
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

