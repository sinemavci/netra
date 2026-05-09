package com.netra.library.enums

import com.netra.library.NetraRequestBody

sealed class Command(val url: String) {
    data class Get(val u: String) : Command(u)

    data class Post(val u: String, val body: NetraRequestBody) : Command(u)

    data class Put(val u: String, val body: NetraRequestBody) : Command(u)

    data class Patch(val u: String, val body: NetraRequestBody) : Command(u)

    data class Delete(val u: String, val body: NetraRequestBody?) : Command(u)
}