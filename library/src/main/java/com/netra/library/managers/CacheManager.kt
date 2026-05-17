package com.netra.library.managers

import android.content.Context
import com.netra.library.Cache
import com.netra.library.observers.CacheEvent
import com.netra.library.NetraClient
import com.netra.library.enums.Command
import java.io.File
import java.security.MessageDigest
import kotlin.Long
import kotlin.String

internal class CacheManager(val context: Context, val command: Command) {
    var cache: Cache? = null
    private fun getCacheKey(command: Command): String {
        val bytes = MessageDigest.getInstance("MD5")
            .digest(command.url.toByteArray() + command.toString().toByteArray())
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
            val cacheFile = File("${cacheDirectory}/${getCacheKey(command)}")

            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            cacheFile.createNewFile()
            cacheFile.writeBytes(bodyBytes)
            NetraClient.memoryCache.put(command.url, bodyBytes)
            NetraClient.notifyCacheEvent(
                CacheEvent.CacheStored(
                    key = "",
                    ageMs = getCacheAgeByMs(cacheFile),
                    sizeByte = bodyBytes.size,
                )
            )
        }
    }

    fun getCacheAllowExpired(): ByteArray? {
        var cacheValue: ByteArray? = null
        val cacheDirectory = context.cacheDir
        val cacheFile = File("${cacheDirectory}/${getCacheKey(command)}")
        if (cacheFile.exists() && cache != null) {
            cacheValue = cacheFile.readBytes()
            NetraClient.notifyCacheEvent(
                CacheEvent.StaleCacheUsed(
                    key = "",
                    ttlMs = cache?.ttl ?: 600000,
                    ageMs = getCacheAgeByMs(cacheFile),
                    expiredByMs = getCacheExpiredByMs(cacheFile, cache?.ttl ?: 600000),
                )
            )
        } else {
            NetraClient.notifyCacheEvent(
                CacheEvent.CacheMiss(
                    key = "",
                )
            )
        }
        return cacheValue
    }

    fun getCacheIfValid(): ByteArray? {
        var cacheValue: ByteArray? = null
        val cacheDirectory = context.cacheDir
        val cacheFile = File("${cacheDirectory}/${getCacheKey(command)}")
        val shouldUseCache = shouldUseCache(cacheFile, cache?.ttl ?: 600000)
        if (cacheFile.exists() && !shouldUseCache) {
            NetraClient.notifyCacheEvent(
                CacheEvent.CacheExpired(
                    key = "",
                    ttlMs = cache?.ttl ?: 600000,
                    ageMs = getCacheAgeByMs(cacheFile),
                    expiredByMs = getCacheExpiredByMs(cacheFile, cache?.ttl ?: 600000),
                )
            )
        } else if (!cacheFile.exists()) {
            NetraClient.notifyCacheEvent(
                CacheEvent.CacheMiss(
                    key = "",
                )
            )
        } else if (cacheFile.exists() && shouldUseCache) {
            cacheValue = cacheFile.readBytes()
            NetraClient.notifyCacheEvent(
                CacheEvent.CacheHit(
                    key = "",
                    ttlMs = cache?.ttl ?: 600000,
                    ageMs = getCacheAgeByMs(cacheFile),
                )
            )
        } else {
            NetraClient.notifyCacheEvent(
                CacheEvent.CacheMiss(
                    key = "",
                )
            )
        }
        return cacheValue
    }
}