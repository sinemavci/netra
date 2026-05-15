package com.netra.library

object EventDispatcher : EventDispatcherProvider {
    var provider: EventDispatcherProvider? = null
        set(value) {
            if (value is EventDispatcher) {
                throw IllegalArgumentException("EventDispatcher cannot be set to itself")
            }
            field = value
        }

    override fun runOnMain(action: () -> Unit) {
        if (provider == null)
            provider = CoroutineEventDispatcherProvider

        provider!!.runOnMain(action)
    }

    override fun runOnBackground(action: () -> Unit) {
        if (provider == null)
            provider = CoroutineEventDispatcherProvider

        provider!!.runOnBackground(action)
    }
}
