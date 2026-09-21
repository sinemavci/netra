package com.netra.library

sealed interface NetraResponse<out T> {
    data class ResponseReceived<out T>(
        val data: T?,
        val statusCode: Int,
        val statusMessage: String?,
        val isCache: Boolean?,
        val headers: Map<String, String>? = emptyMap()
    ): NetraResponse<T>

    data class ResponseQueued(
        val queueOrder: Int,
    ): NetraResponse<Nothing>
}

