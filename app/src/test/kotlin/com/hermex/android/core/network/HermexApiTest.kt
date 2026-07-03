package com.hermex.android.core.network

import com.hermex.android.core.network.dto.LoginRequest
import kotlinx.coroutines.test.runTest
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
}
