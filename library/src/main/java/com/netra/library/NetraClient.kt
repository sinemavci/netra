package com.netra.library

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.collection.LruCache
import com.google.gson.reflect.TypeToken
import com.netra.library.converter.IConverter
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.IOException
import java.io.File
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
val cacheSize = maxMemory / 8
class NetraClient private constructor(
    val context: Context,
    var baseUrl: String? = null,
    var converter: IConverter? = null,
) {
    private val client = OkHttpClient().newBuilder().addInterceptor(NetraInterceptor()).build()

    data class Builder(
        val context: Context,
        var baseUrl: String? = null,
        var converter: IConverter? = null
    ) {
        fun baseUrl(url: String): Builder {
            this.baseUrl = url
            return this
        }

        fun addConverterFactory(netraConverter: IConverter?): Builder {
            this.converter = netraConverter
            return this
        }

        fun setSlowNetworkThreshold(threshold: Long): Builder {
            slowNetworkThreshold = threshold
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
        return RequestBuilder(context, Command.Patch(baseUrl + path, requestBody), client, converter)
    }

    fun delete(path: String, requestBody: RequestBody? = RequestBody.EMPTY): RequestBuilder {
        return RequestBuilder(context, Command.Delete(baseUrl + path, requestBody), client, converter)
    }

    companion object {
        var slowNetworkThreshold: Long = 2000

        var isSlowNetwork: Boolean = false

        internal val globalFailureCount = AtomicInteger(0)

        internal var lastFailureTime: Long = 0

        internal val memoryCache = object : LruCache<String, ByteArray>(cacheSize) {
            override fun sizeOf(key: String, bitmap: ByteArray): Int {
                return bitmap.size / 1024
            }
        }

        internal lateinit var connectivityManager: ConnectivityManager
            private set

        internal fun initCompanion(context: Context) {
            if (!::connectivityManager.isInitialized) {
                connectivityManager = context.applicationContext
                    .getSystemService(ConnectivityManager::class.java)
            }
        }
    }
}

class RequestBuilder(val context: Context, val command: Command, val client: OkHttpClient, val converter: IConverter?) {
    val headers = mutableMapOf<String, String>()

    fun addHeader(key: String, value: String): RequestBuilder {
        headers[key] = value
        return this
    }

    fun slowMode(): RequestBuilder {
        return addHeader("X-Priority", "Slow")
    }

    inline fun <reified T> asList(): NetraCall<List<T>> {
        val type = object : TypeToken<List<T>>() {}.type
        return NetraCall(context, client, command, type, converter, headers)
    }

    inline fun <reified T> asObject(): NetraCall<T> {
        return NetraCall(context, client, command, T::class.java, converter, headers)
    }
}

class StatusReporter(val onStatusUpdate: (Status) -> Unit)

class NetraCall<T>(
    val context: Context,
    val client: OkHttpClient,
    val command: Command,
    val type: Type,
    val converter: IConverter?,
    val header: Map<String, String>?,
) {
    private var _cache: Cache? = null
    private var offlinePolicyAction: OfflinePolicyAction? = null
    private var slowNetworkPolicyAction: SlowNetworkPolicyAction? = null

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun isConnected(): Boolean {
        val network = NetraClient.connectivityManager.activeNetwork ?: return false
        val caps = NetraClient.connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    fun withCache(cache: Cache): NetraCall<T> {
        _cache = cache
        return this
    }

    fun whenSlowNetwork(action: SlowNetworkPolicyAction): NetraCall<T> {
        slowNetworkPolicyAction = action
        return this
    }

    fun whenOffline(action: OfflinePolicyAction): NetraCall<T> {
        offlinePolicyAction = action
        return this
    }

    private fun shouldUseCache(file: File, ttlMillis: Long): Boolean {
        val lastModified = file.lastModified()
        val now = System.currentTimeMillis()
        Log.e("shouldUseCache", "${(now - lastModified) < ttlMillis} ${(now - lastModified)}")
        return (now - lastModified) < ttlMillis
    }

    private fun getCacheKey(command: Command): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(command.url.toByteArray() + command.toString().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun enqueue(callback: (Status?) -> Unit) {
        isConnected()
        val reporter = StatusReporter(callback)
        val request = when (command) {
            is Command.Get -> {
                val requestBuilder = Request.Builder().tag(StatusReporter::class.java, reporter)
                    .url(command.url).get()

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder.build()
            }

            is Command.Post -> {
                val requestBuilder = Request.Builder().tag(StatusReporter::class.java, reporter)
                    .url(command.url)
                    .post(command.body)

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder.build()
            }

            is Command.Put -> {
                val requestBuilder = Request.Builder().tag(StatusReporter::class.java, reporter)
                    .url(command.url)
                    .put(command.body)

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder.build()
            }

            is Command.Patch -> {
                val requestBuilder = Request.Builder().tag(StatusReporter::class.java, reporter)
                    .url(command.url)
                    .patch(command.body)

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder.build()
            }

            is Command.Delete -> {
                val requestBuilder = Request.Builder().tag(StatusReporter::class.java, reporter)
                    .url(command.url)
                    .delete(command.body)

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder.build()
            }
        }

        fun useCachePolicy(e: IOException?) {
            val cacheDirectory = context.cacheDir
            val cacheFile = File("${cacheDirectory}/${getCacheKey(command)}")
            val cacheValue: ByteArray? = if (cacheFile.exists()) {
                cacheFile.readBytes()
            } else {
                null
            }
            if (_cache == null) {
                callback(Status.Failure(e?.message))
            } else if (shouldUseCache(cacheFile, _cache?.ttl ?: 600000)) {
                //val cacheValue = NetraClient.memoryCache.get(baseUrl + path)

                if (cacheValue == null || cacheValue.isEmpty()) {
                    callback(Status.Failure(e?.message))
                } else {
                    if (converter != null) {
                        val convertedResult: T =
                            converter.convert(cacheValue, type)
                        callback(Status.Success(convertedResult, true))
                    } else {
                        //todo
//                    val convertedResult: T =
//                        NetraGsonConverter().convert(response.body.bytes(), type)
                        callback(Status.Success(cacheValue, true))
                    }
                }
            } else {
                callback(Status.Failure(e?.message))
            }
        }

        fun _getNetworkSpeedState(): NetworkSeverity {
            val network =
                NetraClient.connectivityManager.activeNetwork ?: return NetworkSeverity.NORMAL
            val caps = NetraClient.connectivityManager.getNetworkCapabilities(network)
                ?: return NetworkSeverity.NORMAL

            return when {
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) -> NetworkSeverity.DEGRADED
                else -> NetworkSeverity.NORMAL
            }
        }

        fun enqueueCallback(retry: Boolean, onRequest: (retry: Boolean) -> Unit): Callback {
            return object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (isConnected()) {
                        useCachePolicy(e)
                    } else {
                        when (offlinePolicyAction) {
                            OfflinePolicyAction.QUEUE -> {
                                //todo later
                            }

                            OfflinePolicyAction.RETRY -> {
                                if(retry) {
                                    onRequest(false)
                                }
                            }

                            OfflinePolicyAction.USE_CACHE -> {
                                useCachePolicy(e)
                            }

                            OfflinePolicyAction.THROW_ERROR -> {
                                callback(Status.Failure(e.message))
                            }

                            else -> {
                                callback(Status.Failure("Network Error"))
                            }
                        }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        try {
                            val originalBody = response.body
                            val bytes = originalBody.bytes()
                            _cache?.let {
                                val cacheDirectory = context.cacheDir
                                val cacheFile = File("${cacheDirectory}/${getCacheKey(command)}")
                                Log.e("cache file", "cache file re-created: ${cacheFile.name}")
                                if (cacheFile.exists()) {
                                    cacheFile.delete()
                                }
                                cacheFile.createNewFile()
                                cacheFile.writeBytes(bytes)
                            }
                            NetraClient.memoryCache.put(command.url, bytes)
                            val newBody = bytes.toResponseBody(originalBody.contentType())
                            val newResponse = response.newBuilder()
                                .body(newBody)
                                .build()

                            if (converter != null) {
                                val convertedResult: T =
                                    converter.convert(newResponse.body.bytes(), type)
                                callback(Status.Success(convertedResult, false))
                            } else {
                                if (type == ByteArray::class.java) {
                                    @Suppress("UNCHECKED_CAST")
                                    callback(Status.Success(bytes as T, false))
                                } else {
                                    // Fallback for default JSON parsing

                                    //callback(Status.Success( NetraGsonConverter().convert(bytes, type), false))
                                }

                            }
                        } catch (e: Error) {
                            callback(Status.Error(response.code, "Parsing Error: ${e.message}"))
                        } finally {
                            response.close()
                        }
                    } else {
                        callback(Status.Error(response.code, "Server Error: ${response.code}"))
                        response.close()
                    }
                }
            }
        }

        fun onRequest(retry: Boolean) {
            client.newCall(request).enqueue(enqueueCallback(retry) { shouldRetry ->
                onRequest(shouldRetry)
            })
        }

        val networkSeverity = _getNetworkSpeedState()
        Log.e("networkSeverity", "networkSeverity: ${networkSeverity.name}")
        if (networkSeverity == NetworkSeverity.NORMAL) {
            onRequest(true)
        } else {
            when (slowNetworkPolicyAction) {
                SlowNetworkPolicyAction.CANCELABLE -> {
                    val call = client.newCall(request)
                    // Register in a global/local map to allow cancellation
                    //todo
                   // CancelableRegistry.add(command.url, call)
                    onRequest(true)
                }
                SlowNetworkPolicyAction.CACHE -> {
                    useCachePolicy(null)
                }
                SlowNetworkPolicyAction.TIMEOUT -> {
                    val shortClient = client.newBuilder()
                        .readTimeout(1, TimeUnit.SECONDS)
                        .build()

                    // Execute with short client (Note: You'll need to pass this client to .newCall)
                    shortClient.newCall(request).enqueue(enqueueCallback(true, { onRequest(true) }))
                    return
                }
                SlowNetworkPolicyAction.WAIT -> {
                    Handler(Looper.getMainLooper()).postDelayed({
                        onRequest(true)
                    }, 500)
                    return
                }

                else -> onRequest(true)
            }
        }
    }

    //todo
//    fun execute(): T {
//        val response = client.newCall(request).execute()
//        if (converter != null) {
//            val convertedResult: T = converter.convert(response.body.bytes(), type)
//            return convertedResult
//        } else {
//            val convertedResult: T = NetraGsonConverter().convert(response.body.bytes(), type)
//            return convertedResult
//        }
//    }
}

