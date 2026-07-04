package com.netra.library

import android.content.Context
import com.netra.library.converter.IConverter
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicInteger

@PublishedApi
internal class NetraClientConfig(
    val context: Context,
    val client: OkHttpClient,
    val baseUrl: String,
    val converter: IConverter?,
    val globalHeaders: Map<String, String>,
    val globalFailureCount: AtomicInteger = AtomicInteger(0),
    var lastFailureTime: Long = 0
)