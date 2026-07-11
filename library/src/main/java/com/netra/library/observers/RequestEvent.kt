package com.netra.library.observers

import com.netra.library.NetraRequest
import com.netra.library.NetraResponse
import com.netra.library.exceptions.NetraException

sealed interface RequestEvent {
    // Request executed.
    data class RequestExecuted(
        val request: NetraRequest<*>,
    ) : RequestEvent

    // Request completed seamlessly.
    data class RequestSuccess(
        val request: NetraRequest<*>,
        val response: NetraResponse<*>,
    ) : RequestEvent

    // Request failed.
    data class RequestFailed(
        val request: NetraRequest<*>,
        val response: NetraResponse<*>?,
        val exception: NetraException?,
    ) : RequestEvent

}

