package com.netra.library.converter

import com.google.gson.Gson
import java.lang.reflect.Type

class NetraGsonConverter: IConverter {
    override fun <T> convert(bytes: ByteArray, type: Type) : T {
        val json = String(bytes)
        return Gson().fromJson<Any>(json, type) as T
    }
}