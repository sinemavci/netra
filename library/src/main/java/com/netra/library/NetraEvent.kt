package com.netra.library

sealed interface NetworkEvent {
    object Offline : NetworkEvent
    object SlowNetwork : NetworkEvent
    object ConnectionRestored : NetworkEvent
}

sealed interface CacheEvent {
    // Valid cache entry found and used successfully.
    data class CacheHit(
        val key: String,
        val ageMs: Long,
        val ttlMs: Long,
    ) : CacheEvent

    // No cache entry exists.
    data class CacheMiss(
        val key: String,
    ) : CacheEvent

    // Fresh network response successfully written to cache.
    data class CacheStored(
        val key: String,
        val ageMs: Long,
        val sizeByte: Int,
    ) : CacheEvent

    // Cache exists BUT TTL is exceeded.
    data class CacheExpired(
        val key: String,
        val ttlMs: Long,
        val ageMs: Long,
        val expiredByMs: Long,
    ) : CacheEvent

    // Expired cache was used intentionally because policy allowed it.
    data class StaleCacheUsed(
        val key: String,
        val ttlMs: Long,
        val ageMs: Long,
        val expiredByMs: Long,
    ) : CacheEvent
}

sealed interface RequestQueuedEvent {
    // Request could not execute now, so SDK persisted it for later replay.
    data class RequestQueued(
        val key: String,
        val queueOrder: Int,
        val createdAt: Long
    ) : RequestQueuedEvent

    // No cache entry exists.
    data class QueuedRequestRestored(
        val key: String,
    ) : RequestQueuedEvent

    // Fresh network response successfully written to cache.
    data class QueuedRequestExecuted(
        val key: String,
        val response: NetraResponse,
    ) : RequestQueuedEvent

    // Queued network response failed.
    data class QueuedRequestFailed(
        val key: String,
    ) : RequestQueuedEvent
}