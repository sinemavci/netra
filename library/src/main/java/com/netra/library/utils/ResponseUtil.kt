package com.netra.library.utils

import com.netra.library.NetraResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

object ResponseUtil {
    fun convertOkHttpResponseToNetra(response: okhttp3.Response): NetraResponse {
        val bodyBytes = try {
            response.body?.bytes()
        } catch (e: java.io.IOException) {
            null
        }

        return NetraResponse(
            data = mapOf("data" to bodyBytes),
            statusCode = response.code,
            statusMessage = response.message,
            isCache = false,
            headers = response.headers.names().associateWith { response.header(it).orEmpty() }
        )
    }

    fun convertNetraResponseToOkHttp(netraResponse: NetraResponse, request: okhttp3.Request): okhttp3.Response {
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