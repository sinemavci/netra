package com.netra.library.observers

import com.netra.library.NetraResponse
import com.netra.library.exceptions.NetraException

sealed interface QueueEvent {
    // Request could not execute now, so SDK persisted it for later replay.
    data class RequestQueued(
        val url: String,
        val queueOrder: Int,
        val createdAt: Long
    ) : QueueEvent

    // No cache entry exists.
    data class QueuedRequestExecuted(
        val url: String,
    ) : QueueEvent

    // Fresh network response successfully written to cache.
    data class QueuedRequestSuccess(
        val url: String,
        val response: NetraResponse<*>,
    ) : QueueEvent

    // Queued network response failed.
    data class QueuedRequestFailed(
        val url: String,
        val response: NetraResponse<*>?,
        val exception: NetraException?,
    ) : QueueEvent
}