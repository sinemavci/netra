package com.netra.library

interface SdkStatusListener {
    fun attempt(count: Int): Status
}