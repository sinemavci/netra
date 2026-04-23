package com.netra.library

sealed class Status {
    data class Retrying(val code: Int, val attempt: Int) : Status()
    data class Success<T>(val response: T, val isFromCache: Boolean) : Status()
    data class Error(val message: String?) : Status()
}