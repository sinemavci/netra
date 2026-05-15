package com.netra.library

interface EventDispatcherProvider {
    fun runOnMain(action: () -> Unit)

    fun runOnBackground(action: () -> Unit)
}