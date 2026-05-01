package com.netra.library

import android.Manifest
import android.content.Context
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import com.netra.library.converter.IConverter
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.IOException
import java.io.File
import java.lang.reflect.Type
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

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
        val bytes = MessageDigest.getInstance("MD5")
            .digest(command.url.toByteArray() + command.toString().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun cancel() {
        try {
            CancelableStore.cancel(command.url)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun getNetworkSpeedState(): NetworkSeverity {
        val network =
            NetraClient.connectivityManager.activeNetwork ?: return NetworkSeverity.NORMAL
        val caps = NetraClient.connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkSeverity.NORMAL

        return when {
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) -> NetworkSeverity.DEGRADED
            else -> NetworkSeverity.NORMAL
        }
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

        fun enqueueCallback(retry: Boolean, onRequest: (retry: Boolean) -> Unit): Callback {
            return object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    CancelableStore.remove(command.url)
                    if (!call.isCanceled()) {
                        if (isConnected()) {
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
                        } else {
                            when (offlinePolicyAction) {
                                OfflinePolicyAction.QUEUE -> {
                                    //todo
//                                    OfflineQueueManager.push {
//                                        onRequest(true)
//                                    }
                                }

                                OfflinePolicyAction.RETRY -> {
                                    if (retry) {
                                        onRequest(false)
                                    }
                                }

                                OfflinePolicyAction.USE_CACHE -> {
                                    val cacheDirectory = context.cacheDir
                                    val cacheFile =
                                        File("${cacheDirectory}/${getCacheKey(command)}")
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

                                OfflinePolicyAction.THROW_ERROR -> {
                                    callback(Status.Failure(e.message))
                                }

                                else -> {
                                    callback(Status.Failure("Network Error"))
                                }
                            }
                        }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    CancelableStore.remove(command.url)
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
            val call = client.newCall(request)
            CancelableStore.add(command.url, call)
            call.enqueue(enqueueCallback(retry) { shouldRetry ->
                onRequest(shouldRetry)
            })
        }

        val networkSeverity = getNetworkSpeedState()
        Log.e("networkSeverity", "networkSeverity: ${networkSeverity.name}")
        if (networkSeverity == NetworkSeverity.NORMAL) {
            onRequest(true)
        } else {
            if (slowNetworkPolicyAction is SlowNetworkPolicyAction.CACHE) {
                val cacheDirectory = context.cacheDir
                val cacheFile = File("${cacheDirectory}/${getCacheKey(command)}")
                val cacheValue: ByteArray? = if (cacheFile.exists()) {
                    cacheFile.readBytes()
                } else {
                    null
                }
                if (_cache == null) {
                    callback(Status.Failure(null))
                } else if (shouldUseCache(cacheFile, _cache?.ttl ?: 600000)) {
                    //val cacheValue = NetraClient.memoryCache.get(baseUrl + path)

                    if (cacheValue == null || cacheValue.isEmpty()) {
                        callback(Status.Failure(null))
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
                    callback(Status.Failure(null))
                }
            } else if (slowNetworkPolicyAction is SlowNetworkPolicyAction.TIMEOUT) {
                val shortClient = client.newBuilder()
                    .readTimeout(
                        timeout = (slowNetworkPolicyAction as SlowNetworkPolicyAction.TIMEOUT).timeout,
                        TimeUnit.SECONDS
                    )
                    .build()

                shortClient.newCall(request).enqueue(enqueueCallback(true, { onRequest(true) }))
                return
            } else if (slowNetworkPolicyAction is SlowNetworkPolicyAction.WAIT) {
                Handler(Looper.getMainLooper()).postDelayed({
                    onRequest(true)
                }, (slowNetworkPolicyAction as SlowNetworkPolicyAction.WAIT).delay)
                return
            } else {
                onRequest(true)
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