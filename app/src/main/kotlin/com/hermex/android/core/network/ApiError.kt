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

    /** 409 returned by `/api/chat/start` because the session still owns a different
     * `active_stream_id` on the server -- usually a previously-cancelled stream whose server-side
     * cleanup hasn't landed yet. `activeStreamId` is whatever the server reported (or `null` if
     * it didn't include one); the caller can use it to either reattach or wait+retry. */
    class StreamConflict(val activeStreamId: String?) : ApiError(
        "Stream conflict: session already has an active stream" + (activeStreamId?.let { " ($it)" } ?: ""),
    )
}

/** Returns a user-facing message for this error, suitable for display in the UI. */
fun ApiError.userMessage(): String = when (this) {
    is ApiError.Network -> "Could not reach the server. Check your connection and server address."
    is ApiError.Ssl -> "This server uses a certificate Android does not trust. Use HTTPS, or connect to a local server with a trusted certificate."
    is ApiError.Http -> when (statusCode) {
        404 -> "Server not found at this URL."
        409 -> "Previous stream is still stopping. Please wait a moment and try again."
        500 -> "Server error. The server encountered an internal problem."
        else -> "Server returned an error (HTTP $statusCode)."
    }
    is ApiError.Decoding -> "Unexpected response format. Make sure the server is running hermes-webui."
    is ApiError.Unauthorized -> "Session expired. Please sign in again."
    is ApiError.StreamConflict -> "Previous stream is still stopping. Please wait a moment and tap Send again."
}

/**
 * Wraps a [HermexApi] suspend call, translating Retrofit/OkHttp/kotlinx.serialization
 * exceptions into [ApiError]. This is the call-site-local complement to [NetworkModule]'s
 * 401 interceptor: the interceptor drives the app-wide auth-state transition, while this gives
 * the calling repository/ViewModel a typed error it can show in the UI.
 *
 * A 409 with body "session already has an active stream" -- the server's signal that
 * `/api/chat/start` was rejected because a previous stream still owns the session -- is upgraded
 * to [ApiError.StreamConflict] so callers can either reattach to the still-active stream or wait
 * and retry. We deliberately don't use the raw body to match: any 409 with that body is the
 * same logical condition regardless of any extra fields the server may add.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        if (e.code() == 401) throw ApiError.Unauthorized
        // Read the error body exactly once -- okhttp ResponseBody.string() consumes the
        // underlying source, so a second read returns "" and the StreamConflict body check
        // would silently fail (or the subsequent ApiError.Http would lose its body).
        val body: String? = e.response()?.errorBody()?.string()
        if (e.code() == 409 && body != null && body.contains("session already has an active stream")) {
            throw ApiError.StreamConflict(extractActiveStreamId(body))
        }
        throw ApiError.Http(e.code(), body)
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

/** Best-effort extraction of `"active_stream_id":"abc"` (or `"activeStreamId":"abc"`) from a 409
 * error body. Returns null if the field is absent or the body isn't JSON-shaped. Kept private to
 * [safeApiCall] so the contract stays narrow; not exposed as a public helper. */
private fun extractActiveStreamId(body: String): String? {
    val regex = Regex("\"(?:active_stream_id|activeStreamId)\"\\s*:\\s*\"([^\"]*)\"")
    return regex.find(body)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
}
