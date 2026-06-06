package com.netra.library

import okhttp3.Call

data class NetraCall(
    val call: Call,
    val isCancelledWhenDestroy: Boolean,
)