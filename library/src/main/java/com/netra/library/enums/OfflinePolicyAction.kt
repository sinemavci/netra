package com.netra.library.enums

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed class OfflinePolicyAction(val identifier: String) {
    object QUEUE : OfflinePolicyAction("QUEUE")
    object USE_CACHE : OfflinePolicyAction("USE_CACHE")
    data class RETRY(val retries: Int, val retryInterval: Duration? = 2000.milliseconds) : OfflinePolicyAction("RETRY")
    object THROW_ERROR : OfflinePolicyAction("THROW_ERROR")

    companion object {
        fun fromIdentifier(identifier: String, retries: Int?): OfflinePolicyAction {
            return when (identifier) {
                "QUEUE" -> QUEUE
                "USE_CACHE" -> USE_CACHE
                "RETRY" -> RETRY(retries ?: 1, retryInterval = 2000.milliseconds)
                "THROW_ERROR" -> THROW_ERROR
                else -> {
                    throw Throwable("Unknown offline policy: $identifier")
                }
            }
        }
    }
}