package com.netra.library.enums

import com.netra.library.NetraRequestBody

sealed class Command(val url: String, val body: NetraRequestBody = NetraRequestBody.EMPTY) {
    data class Get(val u: String, val bodyParam: NetraRequestBody? = NetraRequestBody.EMPTY) :
        Command(u, bodyParam ?: NetraRequestBody.EMPTY)

    data class Post(val u: String, val bodyParam: NetraRequestBody? = NetraRequestBody.EMPTY) :
        Command(u, bodyParam ?: NetraRequestBody.EMPTY)

    data class Put(val u: String, val bodyParam: NetraRequestBody? = NetraRequestBody.EMPTY) :
        Command(u, bodyParam ?: NetraRequestBody.EMPTY)

    data class Patch(val u: String, val bodyParam: NetraRequestBody? = NetraRequestBody.EMPTY) :
        Command(u, bodyParam ?: NetraRequestBody.EMPTY)

    data class Delete(val u: String, val bodyParam: NetraRequestBody? = NetraRequestBody.EMPTY) :
        Command(u, bodyParam ?: NetraRequestBody.EMPTY)
}