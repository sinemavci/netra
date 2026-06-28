package com.netra.library.enums

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed class SlowNetworkPolicyAction(val identifier: String) {
    data class WAIT(val delay: Long) : SlowNetworkPolicyAction("WAIT")
    data class TIMEOUT(val timeout: Duration) : SlowNetworkPolicyAction("TIMEOUT")
    object USE_CACHE : SlowNetworkPolicyAction("USE_CACHE")

    companion object {
        fun fromIdentifier(
            identifier: String,
            delay: Long?,
            timeout: Duration?
        ): SlowNetworkPolicyAction {
            return when (identifier) {
                "WAIT" -> WAIT(delay ?: 1)
                "USE_CACHE" -> USE_CACHE
                "TIMEOUT" -> TIMEOUT(timeout ?: 1000.milliseconds)
                else -> {
                    throw Throwable("Unknown offline policy: $identifier")
                }
            }
        }
    }
}