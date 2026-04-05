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
        return RequestBuilder(client, path, converter)
    }
}

class RequestBuilder(val client: OkHttpClient, val path: String, val converter: IConverter?) {
    inline fun <reified T> asList(): TypedCall<List<T>> {
        val type = object : TypeToken<List<T>>() {}.type
        return TypedCall(client, path, type, converter)
    }

    inline fun <reified T> asObject(): TypedCall<T> {
        return TypedCall(client, path, T::class.java, converter)
    }
}

class TypedCall<T>(
    val client: OkHttpClient,
    val path: String,
    val type: Type,
    val converter: IConverter?,
) {
    fun enqueue(callback: (T?) -> Unit) {
        val request = Request.Builder().url(path).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("Error: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                println("Data: $body")

                if (converter != null) {
                    val convertedResult: T = converter.convert(response.body.bytes(), type)
                    callback(convertedResult)
                } else {
                    //todo
                }
            }
        })
    }
}