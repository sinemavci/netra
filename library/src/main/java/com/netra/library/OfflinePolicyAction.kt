package com.netra.library

enum class OfflinePolicyAction {
    QUEUE,
    USE_CACHE,
    RETRY, //todo: may be add retry count
    THROW_ERROR
}