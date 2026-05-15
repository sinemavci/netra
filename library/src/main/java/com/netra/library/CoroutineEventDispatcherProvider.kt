package com.netra.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


object CoroutineEventDispatcherProvider : EventDispatcherProvider {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun runOnMain(action: () -> Unit) {
        mainScope.launch { action() }
    }

    override fun runOnBackground(action: () -> Unit) {
        ioScope.launch { action() }
    }
}