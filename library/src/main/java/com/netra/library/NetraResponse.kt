package com.netra.library

data class NetraResponse(
    val data: Map<String, Any?>?,
    val statusCode: Int,
    val statusMessage: String?,
    val isCache: Boolean?,
    val headers: Map<String, String>? = emptyMap()
)