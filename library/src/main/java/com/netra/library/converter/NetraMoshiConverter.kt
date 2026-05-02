package com.netra.library.converter

import com.squareup.moshi.Moshi
import java.lang.reflect.Type

class NetraMoshiConverter: IConverter {
    override fun <T> convert(bytes: ByteArray, type: Type): T {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter<T>(type)
        return adapter.fromJson(bytes.decodeToString())
            ?: throw Exception("Moshi conversion failed")
    }
}