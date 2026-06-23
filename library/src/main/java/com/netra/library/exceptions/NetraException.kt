package com.netra.library.exceptions

sealed class NetraException(
    message: String?,
    cause: Throwable? = null
) : Exception(message, cause)

class NetraTimeoutException(
    cause: Throwable? = null
) : NetraException("Request timeout", cause)

class NetraDnsException(
    cause: Throwable? = null
) : NetraException("DNS resolution failed", cause)

class NetraConnectionException(
    cause: Throwable? = null
) : NetraException("Connection failed", cause)

class NetraSslException(
    cause: Throwable? = null
) : NetraException("SSL error", cause)

class NetraSocketException(
    cause: Throwable? = null
) : NetraException("Socket error", cause)

class NetraNetworkException(
    message: String?,
    cause: Throwable? = null
) : NetraException(message, cause)