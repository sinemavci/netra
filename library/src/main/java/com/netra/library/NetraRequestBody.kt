package com.netra.library

class NetraRequestBody private constructor(
    val content: Any,
    val contentType: String = "application/json; charset=utf-8",
    val isMultipart: Boolean = false
) {
    companion object {
        fun create(json: String, contentType: String = "application/json; charset=utf-8"): NetraRequestBody {
            return NetraRequestBody(json, contentType)
        }

        fun create(bytes: ByteArray, contentType: String): NetraRequestBody {
            return NetraRequestBody(bytes, contentType)
        }

        fun create(map: Map<String, Any?>): NetraRequestBody {
            return NetraRequestBody(map)
        }

        fun multipart(parts: List<NetraPart>): NetraRequestBody {
            return NetraRequestBody(parts, "multipart/form-data", true)
        }
    }
}