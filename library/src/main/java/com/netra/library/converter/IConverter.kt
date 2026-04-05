package com.netra.library.converter

import java.lang.reflect.Type

interface IConverter {
    fun<T> convert(bytes: ByteArray, type: Type): T
}