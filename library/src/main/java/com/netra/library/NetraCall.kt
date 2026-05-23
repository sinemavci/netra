package com.netra.library

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.netra.library.converter.IConverter
import com.netra.library.enums.Command
import com.netra.library.enums.NetworkSeverity
import com.netra.library.enums.OfflinePolicyAction
import com.netra.library.enums.SlowNetworkPolicyAction
import com.netra.library.interceptors.RetryInterceptor
import com.netra.library.managers.CacheManager
import com.netra.library.managers.ObserverManager
import com.netra.library.managers.CancelRequestManager
import com.netra.library.managers.OfflineQueueManager
import com.netra.library.observers.INetraObserver
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.lang.reflect.Type
import java.net.ConnectException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NetraCall<T>(
    val context: Context,
    val client: OkHttpClient,
    val command: Command,
    val type: Type,
    val converter: IConverter?,
    val header: Map<String, String>?,
) {
    private var cacheManager = CacheManager(context, command)
    private var offlinePolicyAction: OfflinePolicyAction? = null
    private var slowNetworkPolicyAction: SlowNetworkPolicyAction? = null
    private var retriesCount: Int? = null
    var executor: ExecutorService? = Executors.newSingleThreadExecutor()
    private var connectivityManager = NetraConnectivityManager.getInstance(context)


    fun withCache(cache: Cache): NetraCall<T> {
        cacheManager.cache = cache
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
            cacheManager.cache = Cache()
        }
        return this
    }

    fun addObserver(observer: INetraObserver): NetraCall<T> {
        if (observer !in ObserverManager.observers) {
            ObserverManager.observers.add(observer)
        }
        return this
    }

    fun removeObserver(observer: INetraObserver): NetraCall<T> {
        ObserverManager.observers.remove(observer)
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

    private fun handleOnFailure(call: Call, e: IOException, callback: (NetraResponse?) -> Unit) {
        CancelRequestManager.remove(command.url)
        if (!call.isCanceled()) {
            val cache = cacheManager.getCacheIfValid()
            if (cache?.isNotEmpty() == true) {
                val response = handleConvertedResponse(cache)
                callback(
                    NetraResponse(
                        data = mapOf("data" to response),
                        statusCode = 200,
                        statusMessage = null,
                        isCache = true,
                    )
                )
            } else {
                callback(getNetraFailedResponse(Exception("Cache not found!")))
            }
        }
    }

    private fun Headers.toMap(): Map<String, String> =
        names().associateWith { name -> get(name)!! }

    private fun handleOnResponse(response: Response, callback: (NetraResponse?) -> Unit) {
        CancelRequestManager.remove(command.url)
        if (response.isSuccessful) {
            try {
                val bodyBytes = response.body?.bytes()
                bodyBytes?.let {
                    cacheManager.writeCacheResponse(it)
                    val convertedResponse = handleConvertedResponse(it)
                    callback(
                        NetraResponse(
                            data = mapOf("data" to convertedResponse),
                            statusCode = 200,
                            statusMessage = null,
                            isCache = false,
                            headers = response.headers.toMap()
                        )
                    )
                }
            } catch (e: Error) {
                callback(getNetraFailedResponse(Exception(e.message)))
            } finally {
                response.close()
            }
        } else {
            response.close()
            callback(getNetraFailedResponse(Exception(response.message)))
        }
    }

    private fun getRequestBody(netraRequestBody: NetraRequestBody): RequestBody {
        val mediaType = netraRequestBody.contentType.toMediaTypeOrNull()
        if (!netraRequestBody.isMultipart) {
            Log.e("", "multipart not heree")
            return when (val content = netraRequestBody.content) {
                is String -> content.toRequestBody(mediaType)
                is ByteArray -> content.toRequestBody(mediaType)
                is Map<*, *> -> {
                    val jsonString = Gson().toJson(content)
                    jsonString.toRequestBody(mediaType)
                }

                else -> "".toRequestBody(null)
            }
        } else {
            Log.e("", "multipart hereee")
            val builder = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            val parts = netraRequestBody.content as List<NetraPart>

            parts.forEach { part ->
                val okHttpBody = when (val content = part.body.content) {
                    is String -> content.toRequestBody(mediaType)
                    is ByteArray -> content.toRequestBody(mediaType)
                    is Map<*, *> -> {
                        val jsonString = Gson().toJson(content)
                        jsonString.toRequestBody(mediaType)
                    }

                    else -> "".toRequestBody(mediaType)
                }
                if (part.filename != null) {
                    builder.addFormDataPart(part.name, part.filename, okHttpBody)
                } else {
                    builder.addFormDataPart(part.name, null, okHttpBody)
                }
            }
            return builder.build()
        }
    }

    private fun getRequest(reporter: StatusReporter?): Request {
        return when (command) {
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
                    .post(getRequestBody(command.body))

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder.build()
            }

            is Command.Put -> {
                val requestBuilder = Request.Builder().tag(StatusReporter::class.java, reporter)
                    .url(command.url)
                    .put(getRequestBody(command.body))

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder.build()
            }

            is Command.Patch -> {
                val requestBuilder = Request.Builder().tag(StatusReporter::class.java, reporter)
                    .url(command.url)
                    .patch(getRequestBody(command.body))

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder.build()
            }

            is Command.Delete -> {
                val requestBuilder = Request.Builder().tag(StatusReporter::class.java, reporter)
                    .url(command.url)
                    .delete(getRequestBody(command.body ?: NetraRequestBody.EMPTY))

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder.build()
            }
        }
    }

    private fun executeCommand(call: Call): NetraResponse {
        val latch = CountDownLatch(1)
        lateinit var _response: NetraResponse
        executor!!.execute({
            try {
                val response = call.execute()
                val bodyBytes = response.body?.bytes()
                bodyBytes?.let {
                    cacheManager.writeCacheResponse(it)
                    _response = NetraResponse(
                        data = mapOf("data" to handleConvertedResponse(it)),
                        statusCode = response.code,
                        statusMessage = response.message,
                        isCache = false,
                        headers = response.headers.toMap()
                    )
                }

            } catch (e: IOException) {
                _response = getNetraFailedResponse(e)
            } finally {
                latch.countDown()
            }
        })
        latch.await()
        return _response
    }

    private fun handleTimeoutPolicy(): OkHttpClient {
        val shortClient = client.newBuilder()
            .callTimeout(
                timeout = (slowNetworkPolicyAction as SlowNetworkPolicyAction.TIMEOUT).timeout,
                TimeUnit.SECONDS
            )
            .build()

        return shortClient
    }

    private fun handleWaitPolicy(request: Request): NetraResponse {
        val latch = CountDownLatch(1)
        lateinit var _netraResponse: NetraResponse
        Log.e("", "slow network policy uses WAIT")
        Handler(Looper.getMainLooper()).postDelayed({
            val call = client.newCall(request)
            CancelRequestManager.add(command.url, call)
            executor!!.execute({
                try {
                    val response = client.newCall(request).execute()
                    _netraResponse = NetraResponse(
                        data = mapOf("data" to response.body?.bytes()),
                        statusCode = response.code,
                        statusMessage = response.message,
                        isCache = false,
                        headers = response.headers.toMap()
                    )
                } catch (e: IOException) {
                    _netraResponse = getNetraFailedResponse(e)
                } finally {
                    latch.countDown()
                }
            })
        }, (slowNetworkPolicyAction as SlowNetworkPolicyAction.WAIT).delay)
        latch.await()
        return _netraResponse
    }

    fun executeStream(onStreamReady: (java.io.InputStream) -> Unit, onFailure: (Exception) -> Unit) {
        val networkSeverity = connectivityManager.getNetworkSpeedState()
        val request = getRequest(null)
        val call = client.newCall(request)

        if (connectivityManager.isConnected()) {
            if (networkSeverity == NetworkSeverity.NORMAL) {
                executor!!.execute {
                    try {
                        val response = call.execute()
                        if (response.isSuccessful) {
                            val inputStream = response.body?.byteStream()
                            if (inputStream != null) {
                                inputStream.use { stream ->
                                    onStreamReady(stream)
                                }
                            } else {
                                onFailure(IOException("Response body is empty"))
                            }
                        } else {
                            onFailure(IOException("Server error: ${response.code}"))
                        }
                    } catch (e: Exception) {
                        Log.e("", "error here: ${e}")
                        onFailure(e)
                    }
                }
            }
            //todo: else
        }
    }

    fun execute(): NetraResponse {
        lateinit var netraResponse: NetraResponse
        val networkSeverity = connectivityManager.getNetworkSpeedState()
        val request = getRequest(null)
        val call = client.newCall(request)

        if (connectivityManager.isConnected()) {
            if (networkSeverity == NetworkSeverity.NORMAL) {
                netraResponse = executeCommand(call)
            } else {
                when (slowNetworkPolicyAction) {
                    is SlowNetworkPolicyAction.USE_CACHE -> {
                        val cache = cacheManager.getCacheAllowExpired()
                        netraResponse = if (cache?.isNotEmpty() == true) {
                            NetraResponse(
                                data = mapOf("data" to handleConvertedResponse(cache)),
                                statusCode = 200,
                                statusMessage = null,
                                isCache = true,
                            )
                        } else {
                            getNetraFailedResponse(Exception("Cache not found!"))
                        }
                    }

                    is SlowNetworkPolicyAction.TIMEOUT -> {
                        val shortClient = handleTimeoutPolicy()
                        netraResponse = executeCommand(shortClient.newCall(request))
                    }

                    is SlowNetworkPolicyAction.WAIT -> {
                       netraResponse = handleWaitPolicy(request)
                    }

                    else -> {
                        val call = client.newCall(request)
                        CancelRequestManager.add(command.url, call)
                        netraResponse = executeCommand(call)
                    }
                }
            }
        } else {
            when (offlinePolicyAction) {
                is OfflinePolicyAction.QUEUE -> {
                    Log.e("", "sdk uses OfflinePolicyAction.QUEUE")
                    OfflineQueueManager.push(call.request())
                    netraResponse = getNetraFailedResponse(null)
                }

                is OfflinePolicyAction.RETRY -> {
                    Log.e("", "sdk uses OfflinePolicyAction.RETRY: ${retriesCount}")
                    retriesCount?.let {
                        val retryClient = OkHttpClient.Builder()
                            .addInterceptor(RetryInterceptor(maxRetries = retriesCount!!))
                            .build()
                        val call = retryClient.newCall(call.request())
                        netraResponse = executeCommand(call)
                    }
                    retriesCount = null
                }

                is OfflinePolicyAction.USE_CACHE -> {
                    val cache = cacheManager.getCacheAllowExpired()
                    netraResponse = if (cache?.isNotEmpty() == true) {
                        NetraResponse(
                            data = mapOf("data" to handleConvertedResponse(cache)),
                            statusCode = 200,
                            statusMessage = null,
                            isCache = true,
                        )
                    } else {
                        getNetraFailedResponse(Exception("Cache not found!"))
                    }
                }

                is OfflinePolicyAction.THROW_ERROR -> {
                    netraResponse = getNetraFailedResponse(ConnectException())
                }

                else -> {
                    netraResponse = getNetraFailedResponse(ConnectException())
                }
            }
        }
        return netraResponse
    }

    private fun enqueueCommand(call: Call, callback: (NetraResponse?) -> Unit) {
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handleOnFailure(call, e, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                handleOnResponse(response, callback)
            }
        })
    }

    fun enqueue(callback: (NetraResponse?) -> Unit) {
        val reporter = StatusReporter(callback)
        val request = getRequest(reporter)
        val networkSeverity = connectivityManager.getNetworkSpeedState()
        val isConnected = connectivityManager.isConnected()

        if (isConnected) {
            if (networkSeverity == NetworkSeverity.NORMAL) {
                val call = client.newCall(request)
                CancelRequestManager.add(command.url, call)
                enqueueCommand(call, callback)
            } else {
                when (slowNetworkPolicyAction) {
                    is SlowNetworkPolicyAction.USE_CACHE -> {
                        val cache = cacheManager.getCacheAllowExpired()
                        if (cache?.isNotEmpty() == true) {
                            callback(
                                NetraResponse(
                                    data = mapOf("data" to handleConvertedResponse(cache)),
                                    statusCode = 200,
                                    statusMessage = null,
                                    isCache = true,
                                )
                            )
                        } else {
                            callback(getNetraFailedResponse(Exception("Cache not found!")))
                        }
                    }

                    is SlowNetworkPolicyAction.TIMEOUT -> {
                        val shortClient = handleTimeoutPolicy()
                        val shortCall = shortClient.newCall(request)
                        enqueueCommand(shortCall, callback)
                        return
                    }

                    is SlowNetworkPolicyAction.WAIT -> {
                        Handler(Looper.getMainLooper()).postDelayed({
                            val call = client.newCall(request)
                            CancelRequestManager.add(command.url, call)
                            enqueueCommand(call, callback)
                        }, (slowNetworkPolicyAction as SlowNetworkPolicyAction.WAIT).delay)
                        return
                    }

                    else -> {
                        val call = client.newCall(request)
                        CancelRequestManager.add(command.url, call)
                        enqueueCommand(call, callback)
                    }
                }
            }
        } else {
            when (offlinePolicyAction) {
                is OfflinePolicyAction.QUEUE -> {
                    Log.e("", "sdk uses OfflinePolicyAction.QUEUE")
                    OfflineQueueManager.push(request)
                }

                is OfflinePolicyAction.RETRY -> {
                    Log.e("", "sdk uses OfflinePolicyAction.RETRY: ${retriesCount}")
                    retriesCount?.let {
                        val retryClient = OkHttpClient.Builder()
                            .addInterceptor(RetryInterceptor(maxRetries = retriesCount!!))
                            .build()
                        val call = retryClient.newCall(request)
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
                }

                is OfflinePolicyAction.USE_CACHE -> {
                    val cache = cacheManager.getCacheAllowExpired()
                    if (cache?.isNotEmpty() == true) {
                        callback(
                            NetraResponse(
                                data = mapOf("data" to handleConvertedResponse(cache)),
                                statusCode = 200,
                                statusMessage = null,
                                isCache = true,
                            )
                        )
                    } else {
                        callback(getNetraFailedResponse(Exception("Cache not found!")))
                    }
                }

                is OfflinePolicyAction.THROW_ERROR -> {
                    callback(getNetraFailedResponse(ConnectException()))
                }

                else -> {
                    callback(getNetraFailedResponse(ConnectException()))
                }
            }
        }
    }

    companion object {
        internal fun getNetraFailedResponse(e: Exception?): NetraResponse {
            val (code, message) = when (e) {
                is ConnectException -> 503 to "Service Unavailable: ${e.message}"
                is java.net.SocketTimeoutException -> 408 to "Request Timeout: ${e.message}"
                is java.net.UnknownHostException -> 502 to "Bad Gateway / DNS failure: ${e.message}"
                is javax.net.ssl.SSLException -> 495 to "SSL Error: ${e.message}"
                is java.net.SocketException -> 503 to "Socket Error: ${e.message}"
                else -> 400 to "IO Error: ${e?.message}"
            }
            return NetraResponse(
                data = null,
                statusCode = code,
                statusMessage = message,
                isCache = false,
            )
        }
    }
}

// todo: when offline or slow network situations, how stream execute manage?
