package com.netra.library.managers

import android.content.Context
import com.netra.library.Cache
import com.netra.library.observers.CacheEvent
import com.netra.library.NetraClient
import com.netra.library.NetraRequest
import java.io.File
import java.security.MessageDigest
import kotlin.Long
import kotlin.String

internal data class MemoryCacheEntry(
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemoryCacheEntry

        if (timestamp != other.timestamp) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
val cacheSize = maxMemory / 8

internal class CacheManager(val context: Context, val request: NetraRequest<*>) {
    var cache: Cache? = null
    private fun getCacheKey(): String {
        val bytes = MessageDigest.getInstance("MD5")
            .digest(request.command.url.toByteArray() + request.command.toString().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getCacheAgeByMs(file: File): Long {
        val lastModified = file.lastModified()
        val now = System.currentTimeMillis()
        return (now - lastModified)
    }

    fun writeCacheResponse(response: ByteArray?) {
        val bodyBytes = response ?: return

        cache?.let {
            val cacheKey = getCacheKey()
            val now = System.currentTimeMillis()
            val cacheFile = File(context.cacheDir, cacheKey)
            NetraClient.memoryCache.put(cacheKey, MemoryCacheEntry(bodyBytes, now))

            try {
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
                cacheFile.writeBytes(bodyBytes)

                val age = getCacheAgeByMs(cacheFile)
                ObserverManager.notifyCacheEvent(
                    CacheEvent.CacheStored(request, age, bodyBytes.size)
                )
            } catch (e: Exception) {
                // Handle file write exceptions gracefully
            }
        }
    }

    fun getCache(allowExpired: Boolean): ByteArray? {
        val cacheKey = getCacheKey()
        val ttl = cache?.ttl ?: Cache.TTL_DEFAULT
        val now = System.currentTimeMillis()

        val memEntry = NetraClient.memoryCache[cacheKey]
        if (memEntry != null) {
            val memAgeMs = now - memEntry.timestamp
            if (memAgeMs < ttl) {
                ObserverManager.notifyCacheEvent(CacheEvent.CacheHit(request, ttl, memAgeMs))
                return memEntry.data
            }
        }

        val cacheFile = File(context.cacheDir, cacheKey)
        if (!cacheFile.exists()) {
            ObserverManager.notifyCacheEvent(CacheEvent.CacheMiss(request))
            return null
        }

        val ageMs = getCacheAgeByMs(cacheFile)
        val isExpired = ageMs >= ttl
        val cacheBytes = cacheFile.readBytes()

        return when {
            !isExpired -> {
                ObserverManager.notifyCacheEvent(CacheEvent.CacheHit(request, ttl, ageMs))
                cacheBytes
            }

            allowExpired -> {
                ObserverManager.notifyCacheEvent(
                    CacheEvent.StaleCacheUsed(request, ttl, ageMs, ageMs - ttl)
                )
                cacheBytes
            }

            else -> {
                ObserverManager.notifyCacheEvent(
                    CacheEvent.CacheExpired(request, ttl, ageMs, ageMs - ttl)
                )
                null
            }
        }
    }
}