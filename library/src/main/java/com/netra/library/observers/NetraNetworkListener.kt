package com.netra.library.observers

import android.util.Log
import com.netra.library.NetraRequest
import com.netra.library.managers.ObserverManager
import okhttp3.Call
import okhttp3.EventListener

internal class NetraNetworkListener: EventListener() {
    override fun callStart(call: Call) {
        val netraRequest = call.request().tag(NetraRequest::class.java)
        if (netraRequest != null) {
            Log.e(
                "",
                "🚀 Netra Call Started: ${call.request().url} id: ${netraRequest.id} command: ${netraRequest.command.toString()}"
            )
            ObserverManager.notifyRequestEvent(
                RequestEvent.RequestExecuted(
                    key = call.request().url.toString(),
                    request = netraRequest
                )
            )
        }
    }
}