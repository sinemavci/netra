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

internal class CacheManager(val context: Context, val request: NetraRequest<*>) {
    var cache: Cache? = null
    private fun getCacheKey(): String {
        val bytes = MessageDigest.getInstance("MD5")
            .digest(request.command.url.toByteArray() + request.command.toString().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getCacheExpiredByMs(file: File, ttlMillis: Long): Long {
        val lastModified = file.lastModified()
        val now = System.currentTimeMillis()
        return (now - lastModified) - ttlMillis
    }

    private fun getCacheAgeByMs(file: File): Long {
        val lastModified = file.lastModified()
        val now = System.currentTimeMillis()
        return (now - lastModified)
    }

    private fun shouldUseCache(file: File, ttlMillis: Long): Boolean {
        val lastModified = file.lastModified()
        val now = System.currentTimeMillis()
        return (now - lastModified) < ttlMillis
    }

    fun writeCacheResponse(response: ByteArray?) {
        val bodyBytes = response ?: return

        cache?.let {
            val cacheDirectory = context.cacheDir
            val cacheFile = File("${cacheDirectory}/${getCacheKey()}")

            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            cacheFile.createNewFile()
            cacheFile.writeBytes(bodyBytes)
            NetraClient.memoryCache.put(request.command.url, bodyBytes)
            ObserverManager.notifyCacheEvent(
                CacheEvent.CacheStored(
                    request = request,
                    ageMs = getCacheAgeByMs(cacheFile),
                    sizeByte = bodyBytes.size,
                )
            )
        }
    }

    fun getCacheAllowExpired(): ByteArray? {
        var cacheValue: ByteArray? = null
        val cacheDirectory = context.cacheDir
        val cacheFile = File("${cacheDirectory}/${getCacheKey()}")
        if (cacheFile.exists() && cache != null) {
            cacheValue = cacheFile.readBytes()
            ObserverManager.notifyCacheEvent(
                CacheEvent.StaleCacheUsed(
                    request = request,
                    ttlMs = cache?.ttl ?: 600000,
                    ageMs = getCacheAgeByMs(cacheFile),
                    expiredByMs = getCacheExpiredByMs(cacheFile, cache?.ttl ?: 600000),
                )
            )
        } else {
            ObserverManager.notifyCacheEvent(
                CacheEvent.CacheMiss(
                    request = request,
                )
            )
        }
        return cacheValue
    }

    fun getCacheIfValid(): ByteArray? {
        var cacheValue: ByteArray? = null
        val cacheDirectory = context.cacheDir
        val cacheFile = File("${cacheDirectory}/${getCacheKey()}")
        val shouldUseCache = shouldUseCache(cacheFile, cache?.ttl ?: 600000)
        if (cacheFile.exists() && !shouldUseCache) {
            ObserverManager.notifyCacheEvent(
                CacheEvent.CacheExpired(
                    request = request,
                    ttlMs = cache?.ttl ?: 600000,
                    ageMs = getCacheAgeByMs(cacheFile),
                    expiredByMs = getCacheExpiredByMs(cacheFile, cache?.ttl ?: 600000),
                )
            )
        } else if (!cacheFile.exists()) {
            ObserverManager.notifyCacheEvent(
                CacheEvent.CacheMiss(
                    request = request,
                )
            )
        } else if (cacheFile.exists() && shouldUseCache) {
            cacheValue = cacheFile.readBytes()
            ObserverManager.notifyCacheEvent(
                CacheEvent.CacheHit(
                    request = request,
                    ttlMs = cache?.ttl ?: 600000,
                    ageMs = getCacheAgeByMs(cacheFile),
                )
            )
        } else {
            ObserverManager.notifyCacheEvent(
                CacheEvent.CacheMiss(
                    request = request,
                )
            )
        }
        return cacheValue
    }
}