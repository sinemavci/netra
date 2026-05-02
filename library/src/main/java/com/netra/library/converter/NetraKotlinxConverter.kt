package com.netra.library.converter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.lang.reflect.Type

class NetraKotlinxConverter : IConverter {
    private val json = Json { ignoreUnknownKeys = true }

    override fun <T> convert(bytes: ByteArray, type: Type): T {
        val element = json.parseToJsonElement(bytes.decodeToString())

        @Suppress("UNCHECKED_CAST")
        return element as T
    }
}

inline fun <reified T> JsonElement.decode(): T =
    Json.decodeFromJsonElement<T>(this)