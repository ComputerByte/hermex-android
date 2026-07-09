package com.hermex.android.core.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/**
 * Unit tests for [safeApiCall] and the [ApiError] hierarchy -- specifically the 409 →
 * [ApiError.StreamConflict] translation added for Issue #10, where the server returns
 * "session already has an active stream" because a previous (raced) cancel hasn't landed yet.
 *
 * Uses `runTest` (which provides its own test dispatcher) rather than `runBlocking` so we
 * never touch the global `Dispatchers.Main` -- other test classes (e.g. ChatViewModelTest)
 * share that global state and a stray `setMain`/`resetMain` from one class would break them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiErrorTest {

    private fun httpException(code: Int, body: String?): HttpException {
        val rb: ResponseBody? = body?.toResponseBody("application/json".toMediaType())
        val response = Response.error<Any>(code, rb ?: "".toResponseBody("application/json".toMediaType()))
        return HttpException(response)
    }

    @Test
    fun `safeApiCall rethrows 409 with 'session already has an active stream' as StreamConflict`() = runTest {
        val body = """{"error":"session already has an active stream","active_stream_id":"stale-1"}"""
        try {
            safeApiCall { throw httpException(409, body) }
            fail("expected StreamConflict")
        } catch (e: ApiError.StreamConflict) {
            assertEquals("stale-1", e.activeStreamId)
        }
    }

    @Test
    fun `safeApiCall rethrows 409 without active_stream_id as StreamConflict with null id`() = runTest {
        val body = """{"error":"session already has an active stream"}"""
        try {
            safeApiCall { throw httpException(409, body) }
            fail("expected StreamConflict")
        } catch (e: ApiError.StreamConflict) {
            assertNull(e.activeStreamId)
        }
    }

    @Test
    fun `safeApiCall does NOT treat a 409 with a different body as StreamConflict`() = runTest {
        // A 409 from a different endpoint (e.g. "etag mismatch", "profile in use") must surface
        // as a generic ApiError.Http, not a StreamConflict -- the recovery path in sendMessage
        // is specific to the chat-start slot.
        val body = """{"error":"etag mismatch"}"""
        try {
            safeApiCall { throw httpException(409, body) }
            fail("expected Http")
        } catch (e: ApiError.Http) {
            assertEquals(409, e.statusCode)
            assertTrue(e.body?.contains("etag mismatch") == true)
        } catch (e: ApiError.StreamConflict) {
            fail("must not be StreamConflict: $e")
        }
    }

    @Test
    fun `safeApiCall still surfaces a 500 as ApiError_Http, not StreamConflict`() = runTest {
        val body = """{"error":"session already has an active stream","active_stream_id":"x"}"""
        try {
            safeApiCall { throw httpException(500, body) }
            fail("expected Http")
        } catch (e: ApiError.Http) {
            assertEquals(500, e.statusCode)
        } catch (e: ApiError.StreamConflict) {
            fail("must not be StreamConflict: $e")
        }
    }

    @Test
    fun `safeApiCall still surfaces 401 as Unauthorized`() = runTest {
        try {
            safeApiCall { throw httpException(401, null) }
            fail("expected Unauthorized")
        } catch (e: ApiError.Unauthorized) {
            // ok
        } catch (e: Exception) {
            fail("expected Unauthorized, got: $e")
        }
    }

    @Test
    fun `safeApiCall wraps IOException as ApiError_Network`() = runTest {
        try {
            safeApiCall<String> { throw IOException("Software caused connection abort") }
            fail("expected Network")
        } catch (e: ApiError.Network) {
            assertTrue(e.cause?.message?.contains("Software caused connection abort") == true)
        }
    }

    @Test
    fun `safeApiCall wraps SSLException as ApiError_Ssl`() = runTest {
        try {
            safeApiCall<String> { throw SSLException("Trust anchor for certification path not found.") }
            fail("expected Ssl")
        } catch (e: ApiError.Ssl) {
            // ok
        } catch (e: Exception) {
            fail("expected Ssl, got: $e")
        }
    }

    @Test
    fun `safeApiCall wraps CertificateException as ApiError_Ssl`() = runTest {
        try {
            safeApiCall<String> { throw CertificateException("Untrusted certificate") }
            fail("expected Ssl")
        } catch (e: ApiError.Ssl) {
            // ok
        } catch (e: Exception) {
            fail("expected Ssl, got: $e")
        }
    }

    @Test
    fun `ApiError_userMessage maps 409 and StreamConflict to a clear stop message`() = runTest {
        val http409 = ApiError.Http(409, "session already has an active stream")
        val conflict = ApiError.StreamConflict("stale-1")
        val msg409 = http409.userMessage()
        val msgConflict = conflict.userMessage()
        assertTrue(msg409.contains("Previous stream is still stopping"))
        assertTrue(msgConflict.contains("Previous stream is still stopping"))
    }
}
