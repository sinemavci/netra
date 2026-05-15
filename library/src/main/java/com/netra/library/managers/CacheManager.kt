package com.netra.library.managers

import android.content.Context
import android.util.Log
import com.netra.library.Cache
import com.netra.library.NetraClient
import com.netra.library.enums.Command
import java.io.File
import java.security.MessageDigest

internal class CacheManager(val context: Context, val command: Command) {
    var cache: Cache? = null
    fun getCacheKey(command: Command): String {
        val bytes = MessageDigest.getInstance("MD5")
            .digest(command.url.toByteArray() + command.toString().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun shouldUseCache(file: File, ttlMillis: Long): Boolean {
        val lastModified = file.lastModified()
        val now = System.currentTimeMillis()
        Log.e("shouldUseCache", "${(now - lastModified) < ttlMillis} ${(now - lastModified)}")
        return (now - lastModified) < ttlMillis
    }

    fun writeCacheResponse(response: ByteArray?) {
        val bodyBytes = response ?: return

        cache?.let {
            val cacheDirectory = context.cacheDir
            val cacheFile = File("${cacheDirectory}/${getCacheKey(command)}")
            Log.e("cache file", "cache file re-created: ${cacheFile.name}")

            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            cacheFile.createNewFile()
            cacheFile.writeBytes(bodyBytes)
            NetraClient.memoryCache.put(command.url, bodyBytes)
        }
    }

    fun getCacheAllowExpired(): ByteArray? {
        val cacheDirectory = context.cacheDir
        val cacheFile = File("${cacheDirectory}/${getCacheKey(command)}")
        val cacheValue: ByteArray? = if (cacheFile.exists() && cache != null) {
            cacheFile.readBytes()
        } else {
            null
        }
        return cacheValue
    }

    fun getCacheIfValid(): ByteArray? {
        val cacheDirectory = context.cacheDir
        val cacheFile = File("${cacheDirectory}/${getCacheKey(command)}")
        val shouldUseCache = shouldUseCache(cacheFile, cache?.ttl ?: 600000)
        val cacheValue: ByteArray? = if (cacheFile.exists() && shouldUseCache) {
            cacheFile.readBytes()
        } else {
            null
        }
        return cacheValue
    }
}