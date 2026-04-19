package com.netra.library

import android.content.Context
import android.util.Log
import androidx.collection.LruCache
import com.google.gson.reflect.TypeToken
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
import java.util.concurrent.atomic.AtomicInteger

val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
val cacheSize = maxMemory / 8
class NetraClient private constructor(
    val context: Context,
    var baseUrl: String? = null,
    var converter: IConverter? = null,
) {
    val client = OkHttpClient().newBuilder().addInterceptor(MyBehaviorInterceptor()).build()

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

        fun build(): NetraClient {
            if (baseUrl != null) {
                return NetraClient(context, baseUrl!!, converter)
            } else {
                throw Exception("Base url not found!")
            }
        }
    }

    fun get(path: String): RequestBuilder {
        return RequestBuilder(context, client, baseUrl!!, path, converter)
    }

    companion object {
        val globalFailureCount = AtomicInteger(0)
        var lastFailureTime: Long = 0

        val memoryCache = object : LruCache<String, ByteArray>(cacheSize) {
            override fun sizeOf(key: String, bitmap: ByteArray): Int {
                return bitmap.size / 1024
            }
        }
    }
}

class RequestBuilder(val context: Context, val client: OkHttpClient, val baseUrl: String, val path: String, val converter: IConverter?) {
    inline fun <reified T> asList(): NetraCall<List<T>> {
        val type = object : TypeToken<List<T>>() {}.type
        return NetraCall(context, client, baseUrl, path, type, converter)
    }

    inline fun <reified T> asObject(): NetraCall<T> {
        return NetraCall(context, client, baseUrl, path, T::class.java, converter)
    }
}

class StatusReporter(val onStatusUpdate: (Status) -> Unit)

class NetraCall<T>(
    val context: Context,
    val client: OkHttpClient,
    val baseUrl: String,
    val path: String,
    val type: Type,
    val converter: IConverter?,
) {
    private var _cache: Cache? = null

    fun withCache(cache: Cache): NetraCall<T> {
        _cache = cache
        return this
    }

    private fun shouldUseCache(file: File, ttlMillis: Long): Boolean {
        val lastModified = file.lastModified()
        val now = System.currentTimeMillis()
        return (now - lastModified) < ttlMillis
    }

    private fun getCacheKey(url: String): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun enqueue(callback: (Status?) -> Unit) {
        val reporter = StatusReporter(callback)
        val request =
            Request.Builder().tag(StatusReporter::class.java, reporter).url(baseUrl + path)
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val cacheDirectory = context.cacheDir
                val cacheFile = File("${cacheDirectory}/${getCacheKey(baseUrl + path)}")
                val cacheValue = cacheFile.readBytes()
                if (_cache == null) {
                    callback(Status.Error(e.message))
                } else if(shouldUseCache(cacheFile, _cache?.ttl ?: 10000)){
                    //val cacheValue = NetraClient.memoryCache.get(baseUrl + path)

                    if (cacheValue.isEmpty()) {
                        callback(Status.Error(e.message))
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
                    callback(Status.Error(e.message))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val originalBody = response.body
                        val bytes = originalBody.bytes()
                        _cache?.let {
                            val cacheDirectory = context.cacheDir
                            val cacheFile = File("${cacheDirectory}/${getCacheKey(baseUrl + path)}")
                            cacheFile.createNewFile()
                            cacheFile.writeBytes(bytes)
                        }
                        NetraClient.memoryCache.put(baseUrl + path, bytes)
                        val newBody = bytes.toResponseBody(originalBody.contentType())
                        val newResponse = response.newBuilder()
                            .body(newBody)
                            .build()

                        if (converter != null) {
                            val convertedResult: T =
                                converter.convert(newResponse.body.bytes(), type)
                            callback(Status.Success(convertedResult, false))
                        } else {
                            //todo
//                    val convertedResult: T =
//                        NetraGsonConverter().convert(response.body.bytes(), type)
                            callback(Status.Success(response.body, false))
                        }
                    } catch (e: Error) {
                        callback(Status.Error("Parsing Error: ${e.message}"))
                    } finally {
                        response.close()
                    }
                } else {
                    callback(Status.Error("Server Error: ${response.code}"))
                    response.close()
                }
            }
        })
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

//todo:
// what are difference retrofit builder vs netra builder?
//  netraClient.get("/large-data")
//   .slowMode() // This sets the header! // here
//   .asObject<MyData>()
//   .enqueue { status -> ... }

