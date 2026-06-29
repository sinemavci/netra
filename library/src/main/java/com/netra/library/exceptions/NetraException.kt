package com.netra.library.exceptions

sealed class NetraException(
    message: String?,
    cause: Throwable? = null
) : Exception(message, cause)

class NetraTimeoutException(
    cause: Throwable? = null
) : NetraException("Request timeout: ${cause?.message}", cause)

class NetraDnsException(
    cause: Throwable? = null
) : NetraException("DNS resolution failed: ${cause?.message}", cause)

class NetraConnectionException(
    cause: Throwable? = null
) : NetraException("Connection failed: ${cause?.message}", cause)

class NetraSslException(
    cause: Throwable? = null
) : NetraException("SSL error: ${cause?.message}", cause)

class NetraSocketException(
    cause: Throwable? = null
) : NetraException("Socket error: ${cause?.message}", cause)

class NetraNetworkException(
    message: String?,
    cause: Throwable? = null
) : NetraException(message + cause?.message, cause)