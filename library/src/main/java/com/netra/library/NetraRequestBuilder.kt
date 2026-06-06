package com.netra.library

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.netra.library.converter.IConverter
import com.netra.library.enums.Command
import okhttp3.OkHttpClient

class NetraRequestBuilder(
    val context: Context,
    val command: Command,
    val client: OkHttpClient,
    val converter: IConverter?,
    clientHeaders: Map<String, String>
) {
    val headers = mutableMapOf<String, String>()

    init {
        clientHeaders.forEach { (key, value) ->
            headers[key] = value
        }
    }

    fun addHeaders(headerParam: Map<String, String>): NetraRequestBuilder {
        headerParam.forEach { (key, value) ->
            headers[key] = value
        }
        return this
    }

    fun slowMode(): NetraRequestBuilder {
        return addHeaders(mapOf("X-Priority" to "Slow"))
    }

    inline fun <reified T> asList(): NetraCall<List<T>> {
        val type = object : TypeToken<List<T>>() {}.type
        return NetraCall(context, client, command, type, converter, headers)
    }

    inline fun <reified T> asObject(): NetraCall<T> {
        return NetraCall(context, client, command, T::class.java, converter, headers)
    }
}