package com.netra.library


sealed class SlowNetworkPolicyAction {
    data class WAIT(val delay: Long) : SlowNetworkPolicyAction()
    data class TIMEOUT(val timeout: Long) : SlowNetworkPolicyAction()
    data object USE_CACHE : SlowNetworkPolicyAction()
}