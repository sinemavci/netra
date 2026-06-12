package com.netra.library

import android.content.Context
import com.netra.library.converter.IConverter
import okhttp3.OkHttpClient

@PublishedApi
internal class NetraConfig(
    val context: Context,
    val client: OkHttpClient,
    val baseUrl: String,
    val converter: IConverter?,
    val globalHeaders: Map<String, String>
)