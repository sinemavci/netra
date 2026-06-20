package com.netra.library.observers

import com.netra.library.NetraResponse

sealed interface QueueEvent {
    // Request could not execute now, so SDK persisted it for later replay.
    data class RequestQueued(
        val key: String,
        val queueOrder: Int,
        val createdAt: Long
    ) : QueueEvent

    // No cache entry exists.
    data class QueuedRequestRestored(
        val key: String,
    ) : QueueEvent

    // Fresh network response successfully written to cache.
    data class QueuedRequestExecuted(
        val key: String,
        val response: NetraResponse,
    ) : QueueEvent

    // Queued network response failed.
    data class QueuedRequestFailed(
        val key: String,
    ) : QueueEvent
}