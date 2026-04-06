package com.netra.library

import com.google.gson.reflect.TypeToken
import com.netra.library.converter.IConverter
import com.netra.library.converter.NetraGsonConverter
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.lang.reflect.Type

class NetraClient private constructor(
    var baseUrl: String? = null,
    var converter: IConverter? = null
) {
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
        val client = OkHttpClient().newBuilder().build()
        return RequestBuilder(client, baseUrl!!, path, converter)
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

class NetraCall<T>(
    val client: OkHttpClient,
    val baseUrl: String,
    val path: String,
    val type: Type,
    val converter: IConverter?,
) {
    val request = Request.Builder().url(baseUrl + path).build()
    fun enqueue(callback: (T?) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("Error: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (converter != null) {
                    val convertedResult: T = converter.convert(response.body.bytes(), type)
                    callback(convertedResult)
                } else {
                    val convertedResult: T =
                        NetraGsonConverter().convert(response.body.bytes(), type)
                    callback(convertedResult)
                }
            }
        })
    }

    fun execute(): T {
        val response = client.newCall(request).execute()
        if (converter != null) {
            val convertedResult: T = converter.convert(response.body.bytes(), type)
            return convertedResult
        } else {
            val convertedResult: T = NetraGsonConverter().convert(response.body.bytes(), type)
            return convertedResult
        }
    }
}

//todo:
// what are difference retrofit builder vs netra builder?
