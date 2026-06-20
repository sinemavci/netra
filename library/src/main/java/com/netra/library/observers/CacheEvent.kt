package com.netra.library.observers

import com.netra.library.NetraRequest

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