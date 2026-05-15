package com.netra.library

sealed interface NetworkEvent {
    object Offline : NetworkEvent
    object SlowNetwork : NetworkEvent

    object ConnectionRestored : NetworkEvent //todo
}

sealed interface CacheEvent {
    object Offline : CacheEvent

    object SlowNetwork : CacheEvent

    object ConnectionRestored : CacheEvent
}