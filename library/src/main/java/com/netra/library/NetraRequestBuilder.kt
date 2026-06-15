package com.netra.library

import com.google.gson.reflect.TypeToken
import com.netra.library.enums.Command

class NetraRequestBuilder internal constructor(
    @PublishedApi internal val config: NetraClientConfig,
    val command: Command
) {
    val headers = mutableMapOf<String, String>()

    init {
        config.globalHeaders.forEach { (key, value) ->
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

    inline fun <reified T> asList(): NetraRequest<List<T>> {
        val type = object : TypeToken<List<T>>() {}.type
        return NetraRequest(config, command, type, headers)
    }

    inline fun <reified T> asObject(): NetraRequest<T> {
        return NetraRequest(config, command, T::class.java, headers)
    }
}