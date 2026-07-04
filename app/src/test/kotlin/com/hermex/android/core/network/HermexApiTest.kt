package com.hermex.android.core.network

import com.hermex.android.core.network.dto.LoginRequest
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HermexApiTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newModule(onUnauthorized: () -> Unit = {}) =
        NetworkModule(FakeCookieStore(), onUnauthorized = onUnauthorized)

    @Test
    fun `health decodes tolerantly with unknown extra fields present`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"ok","sessions":3,"active_streams":1,"uptime_seconds":12.5,"totally_unknown_field":{"nested":true}}""",
            ).setResponseCode(200),
        )
        val api = newModule().createApi(server.url("/").toString())

        val health = safeApiCall { api.health() }

        assertEquals("ok", health.status)
        assertEquals(3, health.sessions)
        assertEquals(1, health.activeStreams)
        assertEquals(12.5, health.uptimeSeconds)
    }

    @Test
    fun `missing optional fields decode as null instead of failing`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}""").setResponseCode(200))
        val api = newModule().createApi(server.url("/").toString())

        val health = safeApiCall { api.health() }

        assertEquals("ok", health.status)
        assertEquals(null, health.sessions)
    }

    @Test
    fun `401 on an authenticated call maps to ApiError Unauthorized and fires the callback`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        var unauthorizedFired = false
        val api = NetworkModule(FakeCookieStore()) { unauthorizedFired = true }.createApi(server.url("/").toString())

        val error = runCatching { safeApiCall { api.authStatus() } }.exceptionOrNull()

        assertTrue(error is ApiError.Unauthorized)
        assertTrue(unauthorizedFired)
    }

    @Test
    fun `401 on login (wrong password) does NOT fire the unauthorized callback`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        var unauthorizedFired = false
        val api = NetworkModule(FakeCookieStore()) { unauthorizedFired = true }.createApi(server.url("/").toString())

        val error = runCatching { safeApiCall { api.login(LoginRequest("wrong-password")) } }.exceptionOrNull()

        assertTrue(error is ApiError.Unauthorized) // still surfaced to the caller...
        assertTrue(!unauthorizedFired) // ...but must not trigger the app-wide auth-state reset
    }

    @Test
    fun `non-2xx maps to ApiError Http with the status code and body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("tunnel unavailable"))
        val api = newModule().createApi(server.url("/").toString())

        val error = runCatching { safeApiCall { api.health() } }.exceptionOrNull()

        assertTrue(error is ApiError.Http)
        assertEquals(503, (error as ApiError.Http).statusCode)
    }

    @Test
    fun `malformed JSON body maps to ApiError Decoding instead of crashing`() = runTest {
        server.enqueue(MockResponse().setBody("not json at all").setResponseCode(200))
        val api = newModule().createApi(server.url("/").toString())

        val error = runCatching { safeApiCall { api.health() } }.exceptionOrNull()

        assertTrue(error is ApiError.Decoding)
    }

    @Test
    fun `uploadAttachment sends session_id and file as multipart fields, no Origin or Referer`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"filename":"photo.png","path":"/state/attachments/sess-1/photo.png","size":3,"mime":"image/png","is_image":true}""",
            ).setResponseCode(200),
        )
        val api = newModule().createApi(server.url("/").toString())

        val sessionIdPart = "sess-1".toRequestBody(null)
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "photo.png",
            byteArrayOf(1, 2, 3).toRequestBody(null),
        )
        val response = safeApiCall { api.uploadAttachment(sessionIdPart, filePart) }

        assertEquals("photo.png", response.filename)
        assertEquals(true, response.isImage)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/upload", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"session_id\""))
        assertTrue(body.contains("sess-1"))
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("filename=\"photo.png\""))
        assertTrue(recorded.getHeader("Origin") == null)
        assertTrue(recorded.getHeader("Referer") == null)
    }

    @Test
    fun `fileRaw sends session_id and path as query parameters`() = runTest {
        server.enqueue(MockResponse().setBody("raw-bytes").setResponseCode(200))
        val api = newModule().createApi(server.url("/").toString())

        val response = safeApiCall { api.fileRaw(sessionId = "sess-1", path = "notes.txt") }

        assertEquals("raw-bytes", response.string())
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/file/raw?session_id=sess-1&path=notes.txt", recorded.path)
    }
}
