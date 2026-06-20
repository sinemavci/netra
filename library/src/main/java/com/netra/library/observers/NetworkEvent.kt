package com.netra.library.observers

sealed interface NetworkEvent {
    object Offline : NetworkEvent
    object SlowNetwork : NetworkEvent
    object ConnectionRestored : NetworkEvent
}