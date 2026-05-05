package com.netra.library.enums

sealed class SlowNetworkPolicyAction(val identifier: String) {
    data class WAIT(val delay: Long) : SlowNetworkPolicyAction("WAIT")
    data class TIMEOUT(val timeout: Long) : SlowNetworkPolicyAction("TIMEOUT")
    object USE_CACHE : SlowNetworkPolicyAction("USE_CACHE")

    companion object {
        fun fromIdentifier(identifier: String, delay: Long?, timeout: Long?): SlowNetworkPolicyAction {
            return when (identifier) {
                "WAIT" -> WAIT(delay ?: 1)
                "USE_CACHE" -> USE_CACHE
                "TIMEOUT" -> TIMEOUT(timeout ?: 1)
                else -> {
                    throw Throwable("Unknown offline policy: $identifier")
                }
            }
        }
    }
}