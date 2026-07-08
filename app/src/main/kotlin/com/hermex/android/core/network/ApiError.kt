package com.hermex.android.core.network

import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : ApiError("Network error: ${cause.message}", cause)
    class Ssl(cause: Throwable) : ApiError("SSL error: ${cause.message}", cause)
    class Http(val statusCode: Int, val body: String?) : ApiError("HTTP $statusCode: ${body ?: "no body"}")
    class Decoding(cause: Throwable) : ApiError("Failed to decode response: ${cause.message}", cause)
    data object Unauthorized : ApiError("Unauthorized (401)")
}

/** Returns a user-facing message for this error, suitable for display in the UI. */
fun ApiError.userMessage(): String = when (this) {
    is ApiError.Network -> "Could not reach the server. Check your connection and server address."
    is ApiError.Ssl -> "This server uses a certificate Android does not trust. Use HTTPS, or connect to a local server with a trusted certificate."
    is ApiError.Http -> when (statusCode) {
        404 -> "Server not found at this URL."
        500 -> "Server error. The server encountered an internal problem."
        else -> "Server returned an error (HTTP $statusCode)."
    }
    is ApiError.Decoding -> "Unexpected response format. Make sure the server is running hermes-webui."
    is ApiError.Unauthorized -> "Session expired. Please sign in again."
}

/**
 * Wraps a [HermexApi] suspend call, translating Retrofit/OkHttp/kotlinx.serialization
 * exceptions into [ApiError]. This is the call-site-local complement to [NetworkModule]'s
 * 401 interceptor: the interceptor drives the app-wide auth-state transition, while this gives
 * the calling repository/ViewModel a typed error it can show in the UI.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        if (e.code() == 401) throw ApiError.Unauthorized
        throw ApiError.Http(e.code(), e.response()?.errorBody()?.string())
    } catch (e: SerializationException) {
        throw ApiError.Decoding(e)
    } catch (e: SSLException) {
        throw ApiError.Ssl(e)
    } catch (e: CertificateException) {
        throw ApiError.Ssl(e)
    } catch (e: IOException) {
        throw ApiError.Network(e)
    }
}
