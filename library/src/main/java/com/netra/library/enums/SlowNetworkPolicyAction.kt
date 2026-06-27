package com.netra.library.enums

import java.util.concurrent.TimeUnit

sealed class SlowNetworkPolicyAction(val identifier: String) {
    data class WAIT(val delay: Long) : SlowNetworkPolicyAction("WAIT")
    data class TIMEOUT(val timeout: Long, val timeUnit: TimeUnit) : SlowNetworkPolicyAction("TIMEOUT")
    object USE_CACHE : SlowNetworkPolicyAction("USE_CACHE")

    companion object {
        fun fromIdentifier(identifier: String, delay: Long?, timeout: Long?, timeUnit: TimeUnit?): SlowNetworkPolicyAction {
            return when (identifier) {
                "WAIT" -> WAIT(delay ?: 1)
                "USE_CACHE" -> USE_CACHE
                "TIMEOUT" -> TIMEOUT(timeout ?: 1, timeUnit ?: TimeUnit.SECONDS)
                else -> {
                    throw Throwable("Unknown offline policy: $identifier")
                }
            }
        }
    }
}