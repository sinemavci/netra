package com.netra.library
data class Cache(
    val ttl: Long = TTL_DEFAULT,
) {
    companion object {
        const val TTL_SHORT = 60000L      // 1 Minute
        const val TTL_DEFAULT = 600000L   // 10 Minutes
        const val TTL_LONG = 3600000L     // 1 Hour
    }
}