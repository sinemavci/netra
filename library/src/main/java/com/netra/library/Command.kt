package com.netra.library

import okhttp3.RequestBody

sealed class Command(val url: String) {
    data class Get(val u: String) : Command(u)

    data class Post(val u: String, val body: RequestBody) : Command(u)

    data class Put(val u: String, val body: RequestBody) : Command(u)

    data class Patch(val u: String, val body: RequestBody) : Command(u)

    data class Delete(val u: String, val body: RequestBody?) : Command(u)
}