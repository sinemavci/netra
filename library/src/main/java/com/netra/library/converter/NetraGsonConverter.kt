package com.netra.library.converter

import com.google.gson.Gson
import java.lang.reflect.Type

class NetraGsonConverter: IConverter {
    override val type: String
        get() = "GSON"

    override fun <T> convert(bytes: ByteArray, type: Type): T {
        val reader = bytes.decodeToString()
        return Gson().fromJson<Any>(reader, type) as T
    }
}