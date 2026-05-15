package com.netra.library.utils

interface EventDispatcherProvider {
    fun runOnMain(action: () -> Unit)

    fun runOnBackground(action: () -> Unit)
}