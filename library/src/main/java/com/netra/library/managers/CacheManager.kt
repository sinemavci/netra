package com.netra.library.managers

import android.content.Context
import androidx.collection.LruCache
import com.google.gson.Gson
import com.netra.library.Cache
import com.netra.library.observers.CacheEvent
import com.netra.library.NetraRequest
import com.netra.library.NetraResponse
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

    fun writeCacheResponse(response: NetraResponse<*>?) {
        cache?.let {
            val cacheKey = getCacheKey()
            val now = System.currentTimeMillis()
            val cacheFile = File(context.cacheDir, cacheKey)
            val data = response?.data
            val json = Gson().toJson(data)
            val byteArray = json.toByteArray(Charsets.UTF_8)
            memoryCache.put(cacheKey, MemoryCacheEntry(byteArray, now))

            try {
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
                cacheFile.writeBytes(byteArray)

                val age = getCacheAgeByMs(cacheFile)
                ObserverManager.notifyCacheEvent(
                    CacheEvent.CacheStored(request, age, byteArray.size)
                )
            } catch (e: Exception) {
                // Handle file write exceptions gracefully
            }
        }
    }

    fun getCache(allowExpired: Boolean): NetraResponse<*>? {
        val cacheKey = getCacheKey()
        val ttl = cache?.ttl ?: Cache.TTL_DEFAULT
        val now = System.currentTimeMillis()

        val memEntry =  memoryCache[cacheKey]
        if (memEntry != null) {
            val memAgeMs = now - memEntry.timestamp
            if (memAgeMs < ttl) {
                ObserverManager.notifyCacheEvent(CacheEvent.CacheHit(request, ttl, memAgeMs))
                val convertedResponse = request.handleConvertedResponse(memEntry.data)
                return NetraResponse(
                    data = convertedResponse,
                    statusCode = 200,
                    statusMessage = null,
                    isCache = true,
                )
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

        val result = when {
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
        if (result != null) {
            val convertedResponse = request.handleConvertedResponse(result)
            return NetraResponse(
                data = convertedResponse,
                statusCode = 200,
                statusMessage = null,
                isCache = true,
            )
        } else {
            return null
        }
    }

    companion object {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8

        val memoryCache = object : LruCache<String, MemoryCacheEntry>(cacheSize) {
            override fun sizeOf(key: String, value: MemoryCacheEntry): Int {
                return value.data.size / 1024
            }
        }
    }
}