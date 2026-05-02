package com.netra.library

sealed class OfflinePolicyAction {
    object QUEUE : OfflinePolicyAction()
    object USE_CACHE : OfflinePolicyAction()
    data class RETRY(val retries: Int) : OfflinePolicyAction()
    object THROW_ERROR : OfflinePolicyAction()
}