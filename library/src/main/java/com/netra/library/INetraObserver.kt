package com.netra.library

interface INetraObserver {
    fun onNetworkChanged(event: NetworkEvent)
}