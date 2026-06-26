package com.netra.library.enums

sealed class OfflinePolicyAction(val identifier: String) {
    object QUEUE : OfflinePolicyAction("QUEUE")
    object USE_CACHE : OfflinePolicyAction("USE_CACHE")
    data class RETRY(val retries: Int, val retryInterval: Long? = 2000) : OfflinePolicyAction("RETRY")
    object THROW_ERROR : OfflinePolicyAction("THROW_ERROR")

    companion object {
        fun fromIdentifier(identifier: String, retries: Int?): OfflinePolicyAction {
            return when (identifier) {
                "QUEUE" -> QUEUE
                "USE_CACHE" -> USE_CACHE
                "RETRY" -> RETRY(retries ?: 1)
                "THROW_ERROR" -> THROW_ERROR
                else -> {
                    throw Throwable("Unknown offline policy: $identifier")
                }
            }
        }
    }
}