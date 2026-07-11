package com.netra.library

data class NetraResponse<T>(
    val data: T?,
    val statusCode: Int,
    val statusMessage: String?,
    val isCache: Boolean?,
    val headers: Map<String, String>? = emptyMap()
)