package com.netra.library.managers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.netra.library.observers.QueueEvent
import com.netra.library.database.NetraDatabase
import com.netra.library.database.PersistentRequest
import com.netra.library.database.QueueDao
import com.netra.library.utils.ResponseUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import java.util.UUID

object OfflineQueueManager {
    private lateinit var dao: QueueDao
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        dao = NetraDatabase.getDatabase(context).queueDao()
    }

    fun push(request: Request) {
        val jsonConverter = Gson()
        scope.launch {
            dao.insertRequest(
                PersistentRequest(
                    id = UUID.randomUUID().toString(),
                    url = request.url.toString(),
                    method = request.method,
                    body = jsonConverter.toJson(request.body),
                    headersJson = jsonConverter.toJson(request.headers.toMultimap()),
                )
            )
            ObserverManager.notifyQueuedEvent(
                QueueEvent.RequestQueued(
                    url = request.url.toString(),
                    queueOrder = dao.getAllRequests().size,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun remove(id: String) {
        scope.launch {
            dao.deleteRequest(id)
        }
    }

    fun processQueue(client: OkHttpClient) {
        val gson = Gson()
        scope.launch {
            //todo: check call is cancelled before?, converted is not enable, and enqueue not exist
            if (dao.getAllRequests().isEmpty()) {
                return@launch
            }
            dao.getAllRequests().forEach { savedReq ->
                val headerMap: Map<String, List<String>> = gson.fromJson(
                    savedReq.headersJson,
                    object : TypeToken<Map<String, List<String>>>() {}.type
                )

                val headerBuilder = Headers.Builder()
                headerMap.forEach { (key, values) ->
                    values.forEach { value -> headerBuilder.add(key, value) }
                }

                val body = when (savedReq.method) {
                    "GET" -> null
                    else -> savedReq.body?.toRequestBody(null)
                }

                val request = Request.Builder()
                    .url(savedReq.url)
                    .method(savedReq.method, body)
                    .headers(headerBuilder.build())
                    .build()
                ObserverManager.notifyQueuedEvent(
                    QueueEvent.QueuedRequestExecuted(
                        url = request.url.toString(),
                    )
                )

                try {
                    val response = client.newCall(request).execute()
                    val netraResponse = ResponseUtil.okHttpResponseToNetra(response, null)
                    if (response.isSuccessful) {
                        dao.deleteRequest(savedReq.id)

                        ObserverManager.notifyQueuedEvent(
                            QueueEvent.QueuedRequestSuccess(
                                url = request.url.toString(),
                                response = netraResponse
                            )
                        )
                    } else {
                        ObserverManager.notifyQueuedEvent(
                            QueueEvent.QueuedRequestFailed(
                                url = request.url.toString(),
                                response = netraResponse,
                                exception = null,
                            )
                        )
                    }
                    response.close()
                } catch (e: IOException) {
                    ObserverManager.notifyQueuedEvent(
                        QueueEvent.QueuedRequestFailed(
                            url = request.url.toString(),
                            response = null,
                            exception = ResponseUtil.mapException(e),
                        )
                    )
                }
                dao.deleteRequest(savedReq.id)
            }
        }
    }
}