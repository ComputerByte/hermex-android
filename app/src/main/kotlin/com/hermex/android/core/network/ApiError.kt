package com.hermex.android.core.network

import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : ApiError("Network error: ${cause.message}", cause)
    class Http(val statusCode: Int, val body: String?) : ApiError("HTTP $statusCode: ${body ?: "no body"}")
    class Decoding(cause: Throwable) : ApiError("Failed to decode response: ${cause.message}", cause)
    data object Unauthorized : ApiError("Unauthorized (401)")
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
    } catch (e: IOException) {
        throw ApiError.Network(e)
    }
}
