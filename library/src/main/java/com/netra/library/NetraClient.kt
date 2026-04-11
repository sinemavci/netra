package com.netra.library

import com.google.gson.reflect.TypeToken
import com.netra.library.converter.IConverter
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicInteger

class NetraClient private constructor(
    var baseUrl: String? = null,
    var converter: IConverter? = null,
) {
    val client = OkHttpClient().newBuilder().addInterceptor(MyBehaviorInterceptor()).build()

    data class Builder(
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
                return NetraClient(baseUrl!!, converter)
            } else {
                throw Exception("Base url not found!")
            }
        }
    }

    fun get(path: String): RequestBuilder {
        return RequestBuilder(client, baseUrl!!, path, converter)
    }

    companion object {
        val globalFailureCount = AtomicInteger(0)
        var lastFailureTime: Long = 0
    }
}

class RequestBuilder(val client: OkHttpClient, val baseUrl: String, val path: String, val converter: IConverter?) {
    inline fun <reified T> asList(): NetraCall<List<T>> {
        val type = object : TypeToken<List<T>>() {}.type
        return NetraCall(client, baseUrl, path, type, converter)
    }

    inline fun <reified T> asObject(): NetraCall<T> {
        return NetraCall(client, baseUrl, path, T::class.java, converter)
    }
}

class StatusReporter(val onStatusUpdate: (Status) -> Unit)

class NetraCall<T>(
    val client: OkHttpClient,
    val baseUrl: String,
    val path: String,
    val type: Type,
    val converter: IConverter?,
) {
    fun enqueue(callback: (Status?) -> Unit) {
        val reporter = StatusReporter(callback)
        val request =
            Request.Builder().tag(StatusReporter::class.java, reporter).url(baseUrl + path).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Status.Error(e.message))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        if (converter != null) {
                            val convertedResult: T = converter.convert(response.body.bytes(), type)
                            callback(Status.Success(convertedResult))
                        } else {
                            //todo
//                    val convertedResult: T =
//                        NetraGsonConverter().convert(response.body.bytes(), type)
                            callback(Status.Success(response.body))
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
