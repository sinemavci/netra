package com.netra.library

import android.Manifest
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.gson.Gson
import com.netra.library.enums.Command
import com.netra.library.enums.NetworkSeverity
import com.netra.library.enums.OfflinePolicyAction
import com.netra.library.enums.SlowNetworkPolicyAction
import com.netra.library.exceptions.NetraException
import com.netra.library.managers.CacheManager
import com.netra.library.managers.ObserverManager
import com.netra.library.managers.CancelRequestManager
import com.netra.library.managers.OfflineQueueManager
import com.netra.library.managers.RetryingCallback
import com.netra.library.observers.INetraObserver
import com.netra.library.observers.RequestEvent
import com.netra.library.utils.ResponseUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.String
import kotlin.time.Duration.Companion.milliseconds

class NetraRequest<T> @PublishedApi internal constructor(
    @PublishedApi internal val config: NetraClientConfig,
    val command: Command,
    val type: Type,
    val header: Map<String, String>?
) {
    var id: String = UUID.randomUUID().mostSignificantBits.toString()
    private var cacheManager = CacheManager(config.context, this)
    private var offlinePolicyAction: OfflinePolicyAction? = null
    private var slowNetworkPolicyAction: SlowNetworkPolicyAction? = null
    private var retriesCount: Int? = null
    private var connectivityManager = NetraConnectivityManager.getInstance(config.context)
    private var isCancelWhenDestroyed = false;

    fun withCache(cache: Cache): NetraRequest<T> {
        if (this.command is Command.Get) {
            cacheManager.cache = cache
            return this
        }

        return this
    }

    fun whenSlowNetwork(action: SlowNetworkPolicyAction): NetraRequest<T> {
        slowNetworkPolicyAction = action
        return this
    }

    fun whenOffline(action: OfflinePolicyAction): NetraRequest<T> {
        offlinePolicyAction = action
        if (offlinePolicyAction is OfflinePolicyAction.RETRY) {
            retriesCount = (offlinePolicyAction as OfflinePolicyAction.RETRY).retries
        } else if (offlinePolicyAction is OfflinePolicyAction.USE_CACHE) {
            cacheManager.cache = Cache()
        }
        return this
    }

    fun cancelWhenDestroyed(): NetraRequest<T> {
        isCancelWhenDestroyed = true
        return this
    }

    fun addObserver(observer: INetraObserver): NetraRequest<T> {
        ObserverManager.addObserver(observer)
        return this
    }

    fun removeObserver(observer: INetraObserver): NetraRequest<T> {
        ObserverManager.removeObserver(observer)
        return this
    }

    fun cancel() {
        try {
            CancelRequestManager.cancel(id)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun handleOnFailure(call: Call, e: IOException, callback: (NetraResponse<T>?, NetraException?) -> Unit) {
        CancelRequestManager.remove(id)
        callback(null, ResponseUtil.mapException(e))
        ObserverManager.notifyRequestEvent(
            RequestEvent.RequestFailed(
                request = this,
                response = null,
                exception = ResponseUtil.mapException(e),
            )
        )
    }

    private fun handleOnResponse(response: Response, callback: (NetraResponse<T>?, NetraException?) -> Unit) {
        CancelRequestManager.remove(id)
        try {
            val _response = ResponseUtil.okHttpResponseToNetra(response, this) as NetraResponse<T>
            callback(_response, null)
            if (response.isSuccessful) {
                cacheManager.writeCacheResponse(_response as NetraResponse<*>?)
                ObserverManager.notifyRequestEvent(
                    RequestEvent.RequestSuccess(
                        request = this,
                        response = _response,
                    )
                )
            } else {
                ObserverManager.notifyRequestEvent(
                    RequestEvent.RequestFailed(
                        request = this,
                        response = _response,
                        exception = null,
                    )
                )
            }
        } catch (e: Exception) {
            callback(null, ResponseUtil.mapException(e))
            ObserverManager.notifyRequestEvent(
                RequestEvent.RequestFailed(
                    request = this,
                    response = null,
                    exception = ResponseUtil.mapException(e),
                )
            )
        } finally {
            response.close()
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

    private fun getRequest(): Request {
        val requestBuilder: Request.Builder = when (command) {
            is Command.Get -> {
                val requestBuilder = Request.Builder().url(command.url).get()

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder
            }

            is Command.Post -> {
                val requestBuilder = Request.Builder().url(command.url)
                    .post(getRequestBody(command.body))

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder
            }

            is Command.Put -> {
                val requestBuilder = Request.Builder().url(command.url)
                    .put(getRequestBody(command.body))

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder
            }

            is Command.Patch -> {
                val requestBuilder = Request.Builder().url(command.url)
                    .patch(getRequestBody(command.body))

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder
            }

            is Command.Delete -> {
                val requestBuilder = Request.Builder().url(command.url)
                    .delete(getRequestBody(command.body))

                header?.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder
            }
        }
        requestBuilder.tag(NetraRequest::class.java, this)
        return requestBuilder.build()
    }

    private suspend fun executeCommand(netraCall: NetraCall): NetraResponse<T> {
        val request = this
        return withContext(Dispatchers.IO) {
            try {
                val response = netraCall.call.execute()
                val netraResponse =
                    ResponseUtil.okHttpResponseToNetra(response, request) as NetraResponse<T>

                if(response.isSuccessful) {
                    cacheManager.writeCacheResponse(netraResponse)
                    ObserverManager.notifyRequestEvent(
                        RequestEvent.RequestSuccess(
                            request = request,
                            response = netraResponse,
                        )
                    )
                } else {
                    ObserverManager.notifyRequestEvent(
                        RequestEvent.RequestFailed(
                            request = request,
                            response = netraResponse,
                            exception = null,
                        )
                    )
                }

                netraResponse
            } catch (e: Exception) {
                throw ResponseUtil.mapException(e)
            }
        }
    }

    private fun timeoutOkHttpBuilder(): OkHttpClient {
        val shortClient = config.client.newBuilder()
            .callTimeout(
                timeout = (slowNetworkPolicyAction as SlowNetworkPolicyAction.TIMEOUT).timeout.inWholeMilliseconds,
                unit = TimeUnit.MILLISECONDS,
            )
            .build()

        return shortClient
    }

    private suspend fun handleWaitPolicy(
        request: Request
    ): NetraResponse<T> {
        delay((slowNetworkPolicyAction as SlowNetworkPolicyAction.WAIT).delay)
        val call = NetraCall(
            config.client.newCall(request),
            isCancelWhenDestroyed
        )

        CancelRequestManager.add(id, call)
        return executeCommand(call)
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun executeStream(
        onStreamReady: (InputStream) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val networkSeverity = connectivityManager.getNetworkSpeedState()
        val request = getRequest()
        val netraCall = NetraCall(config.client.newCall(request), isCancelWhenDestroyed)
        CancelRequestManager.add(id, netraCall)

        if (connectivityManager.isConnected()) {
            if (networkSeverity == NetworkSeverity.NORMAL) {
                return withContext(Dispatchers.IO) {
                    try {
                        val response = netraCall.call.execute()
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

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun execute(): NetraResponse<T> {
        lateinit var netraResponse: NetraResponse<T>
        val networkSeverity = connectivityManager.getNetworkSpeedState()
        val request = getRequest()
        val netraCall = NetraCall(config.client.newCall(request), isCancelWhenDestroyed)

        if (connectivityManager.isConnected()) {
            if (networkSeverity == NetworkSeverity.NORMAL) {
                netraResponse = executeCommand(netraCall)
            } else {
                when (slowNetworkPolicyAction) {
                    is SlowNetworkPolicyAction.USE_CACHE -> {
                        val _response = cacheManager.getCache(allowExpired = true) as NetraResponse<T>?
                        netraResponse = _response ?: executeCommand(netraCall)
                    }

                    is SlowNetworkPolicyAction.TIMEOUT -> {
                        val shortClient = timeoutOkHttpBuilder()
                        val shortCall =
                            NetraCall(shortClient.newCall(request), isCancelWhenDestroyed)
                        netraResponse = executeCommand(shortCall)
                    }

                    is SlowNetworkPolicyAction.WAIT -> {
                        netraResponse = handleWaitPolicy(request)
                    }

                    else -> {
                        val call = NetraCall(config.client.newCall(request), isCancelWhenDestroyed)
                        CancelRequestManager.add(id, call)
                        netraResponse = executeCommand(call)
                    }
                }
            }
        } else {
            when (offlinePolicyAction) {
                is OfflinePolicyAction.QUEUE -> {
                    OfflineQueueManager.push(netraCall.call.request())
                    throw ResponseUtil.mapException(Exception("Request queued for later execution"))
                }

                is OfflinePolicyAction.RETRY -> {
                    var attempt = 0
                    val request = this
                    val maxRetries = retriesCount ?: 1
                    val interval = (offlinePolicyAction as OfflinePolicyAction.RETRY).retryInterval ?: 2000.milliseconds

                    while (true) {
                        try {
                            val activeCall =
                                if (attempt == 0) netraCall.call else netraCall.call.clone()
                            val response = withContext(Dispatchers.IO) {
                                activeCall.execute()
                            }
                            val netraResponse =
                                ResponseUtil.okHttpResponseToNetra(response, request) as NetraResponse<T>
                            if (response.isSuccessful) {
                                cacheManager.writeCacheResponse(netraResponse)
                                ObserverManager.notifyRequestEvent(
                                    RequestEvent.RequestSuccess(
                                        request = this,
                                        response = netraResponse,
                                    )
                                )
                            } else {
                                ObserverManager.notifyRequestEvent(
                                    RequestEvent.RequestFailed(
                                        request = this,
                                        response = netraResponse,
                                        exception = null,
                                    )
                                )
                            }
                            return netraResponse
                        } catch (e: Exception) {
                            if (e is IllegalStateException && e.message?.contains("Already Executed") == true) {
                                throw e
                            }

                            attempt++
                            if (attempt >= maxRetries) {
                                throw ResponseUtil.mapException(e)
                            }

                            delay(interval)
                        }
                        retriesCount = null
                    }
                }

                is OfflinePolicyAction.USE_CACHE -> {
                    netraResponse = cacheManager.getCache(allowExpired = true) as NetraResponse<T>? ?: executeCommand(netraCall)
                }

                is OfflinePolicyAction.THROW_ERROR -> {
                    netraResponse = executeCommand(netraCall)
                }

                else -> {
                    netraResponse = executeCommand(netraCall)
                }
            }
        }
        return netraResponse
    }

    private fun enqueueCommand(netraCall: NetraCall, callback: (NetraResponse<T>?, NetraException?) -> Unit) {
        CancelRequestManager.add(id, netraCall)
        netraCall.call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handleOnFailure(call, e, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                handleOnResponse(response, callback)
            }
        })
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun enqueue(callback: (NetraResponse<T>?, NetraException?) -> Unit) {
        val request = getRequest()
        val networkSeverity = connectivityManager.getNetworkSpeedState()
        val isConnected = connectivityManager.isConnected()
        val call = NetraCall(config.client.newCall(request), isCancelWhenDestroyed)

        if (isConnected) {
            if (networkSeverity == NetworkSeverity.NORMAL) {
                enqueueCommand(call, callback)
            } else {
                when (slowNetworkPolicyAction) {
                    is SlowNetworkPolicyAction.USE_CACHE -> {
                        val cacheResponse = cacheManager.getCache(allowExpired = true) as NetraResponse<T>?
                        if (cacheResponse != null) {
                            callback(cacheResponse, null)
                            ObserverManager.notifyRequestEvent(
                                RequestEvent.RequestSuccess(
                                    request = this,
                                    response = cacheResponse,
                                )
                            )
                        } else {
                            enqueueCommand(call, callback)
                        }
                    }

                    is SlowNetworkPolicyAction.TIMEOUT -> {
                        val shortClient = timeoutOkHttpBuilder()
                        val shortCall =
                            NetraCall(shortClient.newCall(request), isCancelWhenDestroyed)
                        enqueueCommand(shortCall, callback)
                    }

                    is SlowNetworkPolicyAction.WAIT -> {
                        Handler(Looper.getMainLooper()).postDelayed({
                            enqueueCommand(call, callback)
                        }, (slowNetworkPolicyAction as SlowNetworkPolicyAction.WAIT).delay.inWholeMilliseconds)
                    }

                    else -> {
                        enqueueCommand(call, callback)
                    }
                }
            }
        } else {
            when (offlinePolicyAction) {
                is OfflinePolicyAction.QUEUE -> {
                    OfflineQueueManager.push(request)
                }

                is OfflinePolicyAction.RETRY -> {
                    val interval = (offlinePolicyAction as OfflinePolicyAction.RETRY).retryInterval ?: 2000.milliseconds
                    retriesCount?.let {
                        call.call.enqueue(object : RetryingCallback(config.client, maxRetries = it, interval) {
                            override fun onRetryFailure(call: Call, e: okio.IOException) {
                                handleOnFailure(call, e, callback)
                            }

                            override fun onRetryResponse(call: Call, response: Response) {
                                handleOnResponse(response, callback)
                            }
                        })
                    }
                    retriesCount = null
                }

                is OfflinePolicyAction.USE_CACHE -> {
                    val cacheResponse = cacheManager.getCache(allowExpired = true) as NetraResponse<T>?
                    if (cacheResponse != null) {
                        callback(cacheResponse, null)
                        ObserverManager.notifyRequestEvent(
                            RequestEvent.RequestSuccess(
                                request = this,
                                response = cacheResponse,
                            )
                        )
                    } else {
                        enqueueCommand(call, callback)
                    }
                }

                is OfflinePolicyAction.THROW_ERROR -> {
                    enqueueCommand(call, callback)
                }

                else -> {
                    enqueueCommand(call, callback)
                }
            }
        }
    }

    fun toConfig(): NetraRequestConfig {
        return NetraRequestConfig(
            id = id,
            url = command.url,
            headers = header,
            offlinePolicy = offlinePolicyAction,
            slowNetworkPolicy = slowNetworkPolicyAction,
            cancelOnDispose = isCancelWhenDestroyed,
            cache = cacheManager.cache,
            body = command.body
        )
    }

    internal fun handleConvertedResponse(byteArray: ByteArray): T {
        if (config.converter != null) {
            val convertedResult: T =
                config.converter.convert(byteArray, type)
            return convertedResult
        } else {
            @Suppress("UNCHECKED_CAST")
            return byteArray as T
        }
    }
}

// todo: when offline or slow network situations, how stream execute manage?
