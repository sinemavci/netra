package com.netra.library

import okhttp3.Call
import com.netra.library.converter.IConverter

data class NetraCall(
    val call: Call,
    val converter: IConverter?,
    val isCancelledWhenDestroy: Boolean,
)