package com.netra.library

class NetraPart private constructor(
    val name: String,
    val filename: String? = null,
    val body: NetraRequestBody
) {
    companion object {
        fun formData(name: String, value: String): NetraPart {
            return NetraPart(name, null, NetraRequestBody.create(value, "text/plain"))
        }

        fun file(name: String, filename: String, bytes: ByteArray, contentType: String): NetraPart {
            return NetraPart(name, filename, NetraRequestBody.create(bytes, contentType))
        }
    }
}