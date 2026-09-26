package com.netra.library.managers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.netra.library.NetraCall
import com.netra.library.NetraResponse
import com.netra.library.converter.IConverter
import com.netra.library.converter.NetraGsonConverter
import com.netra.library.converter.NetraKotlinxConverter
import com.netra.library.converter.NetraMoshiConverter
import com.netra.library.observers.QueueEvent
import com.netra.library.database.NetraDatabase
import com.netra.library.database.DeferredRequestEntity
import com.netra.library.database.DeferredDao
import com.netra.library.enums.DeferredStatus
import com.netra.library.utils.ResponseUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

object DeferredRequestManager {
    private lateinit var dao: DeferredDao

    private lateinit var applicationContext: Context
    private val client = OkHttpClient()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    const val MAX_RETRIES = 3

    fun init(context: Context) {
        dao = NetraDatabase.getDatabase(context).deferredDao()
        applicationContext = context
    }

    class NetraTransferWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val id = inputData.getString("deferredWorkId") ?: return Result.failure()
            val entity = dao.getRequest(id)
            val gson = Gson()

            if (entity.status == DeferredStatus.SUCCEEDED) return Result.success()

            dao.updateStatus(id, DeferredStatus.RUNNING)

            val headerMap: Map<String, List<String>> = gson.fromJson(
                entity.headersJson,
                object : TypeToken<Map<String, List<String>>>() {}.type
            )

            val headerBuilder = Headers.Builder()
            headerMap.forEach { (key, values) ->
                values.forEach { value -> headerBuilder.add(key, value) }
            }

            val converter: IConverter? = when (entity.converter) {
                "GSON" -> {
                    NetraGsonConverter()
                }

                "MOSHI" -> {
                    NetraMoshiConverter()
                }

                "KOTLINX" -> {
                    NetraKotlinxConverter()
                }

                else -> {
                    null
                }
            }

            val body = when (entity.method) {
                "GET" -> null
                else -> entity.body?.toRequestBody(null)
            }

            val request = Request.Builder()
                .url(entity.url)
                .method(entity.method, body)
                .headers(headerBuilder.build())
                .build()
            ObserverManager.notifyQueuedEvent(
                QueueEvent.QueuedRequestExecuted(
                    url = request.url.toString(),
                )
            )

            return try {
                val okHttpResponse = client.newCall(request).execute()
                val byteArray = okHttpResponse.body?.bytes()
                val convertedResponse = try {
                    if (byteArray != null) {
                        converter?.convert<Any>(byteArray, Any::class.java) ?: byteArray
                    } else {
                        null
                    }
                } catch (e: java.io.IOException) {
                    null
                }

                val netraResponse = NetraResponse.ResponseReceived(
                    data = convertedResponse,
                    statusCode = okHttpResponse.code,
                    statusMessage = okHttpResponse.message,
                    isCache = false,
                    headers = okHttpResponse.headers.toMap()
                )
                dao.deleteRequest(entity.id)

                if (okHttpResponse.isSuccessful) {
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
                dao.updateStatus(id, DeferredStatus.SUCCEEDED)
                okHttpResponse.close()
                Result.success()
            } catch (e: Exception) {
                ObserverManager.notifyQueuedEvent(
                    QueueEvent.QueuedRequestFailed(
                        url = request.url.toString(),
                        response = null,
                        exception = ResponseUtil.mapException(e),
                    )
                )
                dao.updateStatus(id, DeferredStatus.FAILED)
                //todo
//                if (runAttemptCount < MAX_RETRIES) {
//                    Result.retry()
//                } else {
//                    ObserverManager.notifyQueuedEvent(
//                        QueueEvent.QueuedRequestFailed(
//                            url = request.url.toString(),
//                            response = null,
//                            exception = ResponseUtil.mapException(e),
//                        )
//                    )
                    Result.failure()
//                }
            }
        }
    }

    fun enqueueDeferredRequest(netraCall: NetraCall): Int {
        val jsonConverter = Gson()
        var queueOrder = 0
        scope.launch {
            val request = netraCall.call.request()
            val converterStr = when (netraCall.converter) {
                is NetraGsonConverter -> "GSON"
                is NetraMoshiConverter -> "MOSHI"
                is NetraKotlinxConverter -> "KOTLINX"
                else -> null
            }

            val entity = DeferredRequestEntity(
                id = UUID.randomUUID().toString(),
                url = request.url.toString(),
                method = request.method,
                body = jsonConverter.toJson(request.body),
                headersJson = jsonConverter.toJson(request.headers.toMultimap()),
                converterStr,
                DeferredStatus.PENDING,
            )

            dao.insertRequest(entity)

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                "deferredWorkId" to entity.id
            )
            val requestBuilder = OneTimeWorkRequestBuilder<NetraTransferWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag("netra_deferred_work")
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                entity.id,
                ExistingWorkPolicy.KEEP,
                requestBuilder
            )
            queueOrder = dao.getAllRequests(DeferredStatus.PENDING).size
            ObserverManager.notifyQueuedEvent(
                QueueEvent.RequestQueued(
                    url = request.url.toString(),
                    queueOrder = queueOrder,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        return queueOrder
    }
}