package com.netra.library

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException
import java.util.Timer
import java.util.TimerTask

abstract class RetryingCallback(
    private val client: OkHttpClient,
    private val maxRetries: Int,
    private var internal: Long,
    private var attempt: Int = 0,
) : Callback {

    abstract fun onRetryFailure(call: Call, e: IOException)
    abstract fun onRetryResponse(call: Call, response: Response)

    override fun onFailure(call: Call, e: IOException) {
        if (attempt < maxRetries) {
            attempt++
            val delayMs = internal * attempt

            Timer().schedule(object : TimerTask() {
                override fun run() {
                    client.newCall(call.request()).enqueue(this@RetryingCallback)
                }
            }, delayMs)
        } else {
            onRetryFailure(call, e)
        }
    }

    override fun onResponse(call: Call, response: Response) {
            onRetryResponse(call, response)
    }
}