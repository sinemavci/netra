package com.netra.library.managers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.netra.library.NetraResponse
import com.netra.library.converter.NetraGsonConverter
import com.netra.library.observers.RequestEvent
import com.netra.library.database.NetraDatabase
import com.netra.library.database.PersistentRequest
import com.netra.library.database.QueueDao
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
                RequestEvent.RequestQueued(
                    key = request.url.toString(),
                    queueOrder = dao.getAllRequests().size,
                    createdAt = System.currentTimeMillis()
                )
            )
            Log.e("Netra", "Request persisted to DB: ${request.url}")
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
                    RequestEvent.QueuedRequestRestored(
                        key = request.url.toString(),
                    )
                )

                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        dao.deleteRequest(savedReq.id)
                        //todo: get converted setted in client
                        val convertedResult =
                            NetraGsonConverter().convert<Any>(response.body!!.bytes(), Any::class.java)

                        ObserverManager.notifyQueuedEvent(
                            RequestEvent.QueuedRequestExecuted(
                                key = request.url.toString(),
                                response = NetraResponse(
                                    data = mapOf("data" to convertedResult),
                                    statusCode = response.code,
                                    statusMessage = response.message,
                                    isCache = false,
                                    headers = response.headers.toMap()
                                )
                            )
                        )
                        Log.d("Netra", "Successfully synced: ${savedReq.url} ${response.body}")
                    } else {
                        ObserverManager.notifyQueuedEvent(
                            RequestEvent.QueuedRequestFailed(
                                key = request.url.toString(),
                            )
                        )
                    }
                    response.close()
                } catch (e: IOException) {
                    Log.e("Netra", "Sync failed for ${savedReq.url}, keeping in queue.")
                    ObserverManager.notifyQueuedEvent(
                        RequestEvent.QueuedRequestFailed(
                            key = request.url.toString(),
                        )
                    )
                }
                dao.deleteRequest(savedReq.id)
            }
        }
    }
}