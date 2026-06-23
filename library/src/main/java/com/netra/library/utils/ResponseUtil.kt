package com.netra.library.utils

import com.netra.library.NetraRequest
import com.netra.library.NetraResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

internal object ResponseUtil {
    fun okHttpResponseToNetra(response: okhttp3.Response, request: NetraRequest<*>): NetraResponse {
        val convertedResponse = try {
            val byteArray = response.body?.bytes()
            if (byteArray != null) {
                request.handleConvertedResponse(byteArray)
            } else {
                null
            }
        } catch (e: java.io.IOException) {
            null
        }

        return NetraResponse(
            data = mapOf("data" to convertedResponse),
            statusCode = response.code,
            statusMessage = response.message,
            isCache = false,
            headers = response.headers.toMap()
        )
    }

    fun netraResponseToOkHttp(
        netraResponse: NetraResponse,
        request: okhttp3.Request
    ): okhttp3.Response {
        val jsonString = com.google.gson.Gson().toJson(netraResponse.data)

        val responseBody = jsonString.toResponseBody("application/json".toMediaTypeOrNull())

        val headersBuilder = okhttp3.Headers.Builder()
        netraResponse.headers?.forEach { (key, value) ->
            headersBuilder.add(key, value)
        }

        return okhttp3.Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(netraResponse.statusCode)
            .message(netraResponse.statusMessage ?: "")
            .body(responseBody)
            .headers(headersBuilder.build())
            .build()
    }
}