package com.netra.library

import android.Manifest
import android.content.Context
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import com.netra.library.converter.IConverter
import com.netra.library.enums.Command
import com.netra.library.enums.NetworkSeverity
import com.netra.library.enums.OfflinePolicyAction
import com.netra.library.enums.SlowNetworkPolicyAction
import com.netra.library.enums.Status
import com.netra.library.interceptors.RetryInterceptor
import com.netra.library.managers.CancelRequestManager
import com.netra.library.managers.OfflineQueueManager
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

    private var retriesCount: Int? = null

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
        if (offlinePolicyAction is OfflinePolicyAction.RETRY) {
            retriesCount = (offlinePolicyAction as OfflinePolicyAction.RETRY).retries
        } else if (offlinePolicyAction is OfflinePolicyAction.USE_CACHE) {
            _cache = Cache(null)
        }
        return this
    }

    fun cancel() {
        try {
            CancelRequestManager.cancel(command.url)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun handleConvertedResponse(byteArray: ByteArray): T {
        if (converter != null) {
            val convertedResult: T =
                converter.convert(byteArray, type)
            return convertedResult
        } else {
            @Suppress("UNCHECKED_CAST")
            return byteArray as T
        }
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

    private fun handleOnFailure(call: Call, e: IOException, callback: (Status?) -> Unit) {
        CancelRequestManager.remove(command.url)
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
                    callback(Status.Failure(e.message))
                } else if (shouldUseCache(cacheFile, _cache?.ttl ?: 600000)) {
                    //val cacheValue = NetraClient.memoryCache.get(baseUrl + path)

                    if (cacheValue == null || cacheValue.isEmpty()) {
                        callback(Status.Failure(e.message))
                    } else {
                        val response = handleConvertedResponse(cacheValue)
                        callback(Status.Success(response, true))
                    }
                } else {
                    callback(Status.Failure(e.message))
                }
            } else {
                if (offlinePolicyAction is OfflinePolicyAction.QUEUE) {
                    Log.e("", "sdk uses OfflinePolicyAction.QUEUE")
                    OfflineQueueManager.push(call.request())
                } else if (offlinePolicyAction is OfflinePolicyAction.RETRY) {
                    Log.e("", "sdk uses OfflinePolicyAction.RETRY: ${retriesCount}")
                    retriesCount?.let {
                        val retryClient = OkHttpClient.Builder()
                            .addInterceptor(RetryInterceptor(maxRetries = retriesCount!!))
                            .build()
                        val call = retryClient.newCall(call.request())
                        call.enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                handleOnFailure(call, e, callback)
                            }

                            override fun onResponse(call: Call, response: Response) {
                                handleOnResponse(response, callback)
                            }
                        })
                    }
                    retriesCount = null
                } else if (offlinePolicyAction is OfflinePolicyAction.USE_CACHE) {
                    val cacheDirectory = context.cacheDir
                    val cacheFile =
                        File("${cacheDirectory}/${getCacheKey(command)}")
                    val cacheValue: ByteArray? = if (cacheFile.exists()) {
                        cacheFile.readBytes()
                    } else {
                        null
                    }
                    if (cacheValue == null) {
                        callback(Status.Failure(e.message))
                    } else if (shouldUseCache(cacheFile, _cache?.ttl ?: 600000)) {
                        //val cacheValue = NetraClient.kt.memoryCache.get(baseUrl + path)

                        if (cacheValue.isEmpty()) {
                            callback(Status.Failure(e.message))
                        } else {
                            val response = handleConvertedResponse(cacheValue)
                            callback(Status.Success(response, true))
                        }
                    } else {
                        callback(Status.Failure(e.message))
                    }
                } else if (offlinePolicyAction is OfflinePolicyAction.THROW_ERROR) {
                    callback(Status.Failure(e.message))
                } else {
                    callback(Status.Failure("Network Error"))
                }
            }
        }
    }

    private fun handleOnResponse(response: Response, callback: (Status?) -> Unit) {
        CancelRequestManager.remove(command.url)
        if (response.isSuccessful) {
            try {
                val originalBody = response.body
                val bytes = originalBody?.bytes()
                bytes?.let {
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

                    val convertedResponse = handleConvertedResponse(newResponse.body!!.bytes())
                    callback(Status.Success(convertedResponse, false))
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

    fun enqueue(callback: (Status?) -> Unit) {
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

        val networkSeverity = getNetworkSpeedState()
        if (networkSeverity == NetworkSeverity.NORMAL) {
            val call = client.newCall(request)
            CancelRequestManager.add(command.url, call)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    handleOnFailure(call, e, callback)
                }

                override fun onResponse(call: Call, response: Response) {
                    handleOnResponse(response, callback)
                }
            })
        } else {
            if (slowNetworkPolicyAction is SlowNetworkPolicyAction.USE_CACHE) {
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
                        val response = handleConvertedResponse(cacheValue)
                        callback(Status.Success(response, true))
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

                shortClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        handleOnFailure(call, e, callback)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        handleOnResponse(response, callback)
                    }
                })
                return
            } else if (slowNetworkPolicyAction is SlowNetworkPolicyAction.WAIT) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val call = client.newCall(request)
                    CancelRequestManager.add(command.url, call)
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            handleOnFailure(call, e, callback)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            handleOnResponse(response, callback)
                        }
                    })
                }, (slowNetworkPolicyAction as SlowNetworkPolicyAction.WAIT).delay)
                return
            } else {
                val call = client.newCall(request)
                CancelRequestManager.add(command.url, call)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        handleOnFailure(call, e, callback)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        handleOnResponse(response, callback)
                    }
                })
            }
        }
    }
}