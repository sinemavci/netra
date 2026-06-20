package com.netra.library.observers

import com.netra.library.NetraRequest
import com.netra.library.NetraResponse

sealed interface NetworkEvent {
    object Offline : NetworkEvent
    object SlowNetwork : NetworkEvent
    object ConnectionRestored : NetworkEvent
}

sealed interface CacheEvent {
    // Valid cache entry found and used successfully.
    data class CacheHit(
        val request: NetraRequest<*>,
        val ageMs: Long,
        val ttlMs: Long,
    ) : CacheEvent

    // No cache entry exists.
    data class CacheMiss(
        val request: NetraRequest<*>,
    ) : CacheEvent

    // Fresh network response successfully written to cache.
    data class CacheStored(
        val request: NetraRequest<*>,
        val ageMs: Long,
        val sizeByte: Int,
    ) : CacheEvent

    // Cache exists BUT TTL is exceeded.
    data class CacheExpired(
        val request: NetraRequest<*>,
        val ttlMs: Long,
        val ageMs: Long,
        val expiredByMs: Long,
    ) : CacheEvent

    // Expired cache was used intentionally because policy allowed it.
    data class StaleCacheUsed(
        val request: NetraRequest<*>,
        val ttlMs: Long,
        val ageMs: Long,
        val expiredByMs: Long,
    ) : CacheEvent
}

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

sealed interface RequestEvent {
    // Request executed.
    data class RequestExecuted(
        val request: NetraRequest<*>,
    ) : RequestEvent

    // Request completed seamlessly.
    data class RequestSuccess(
        val request: NetraRequest<*>,
        val response: NetraResponse,
    ) : RequestEvent

    // Request failed.
    data class RequestFailed(
        val request: NetraRequest<*>,
        val response: NetraResponse,
    ) : RequestEvent

}

