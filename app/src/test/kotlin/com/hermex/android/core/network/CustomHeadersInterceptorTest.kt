package com.hermex.android.core.network

import com.hermex.android.core.network.dto.LoginRequest
import com.hermex.android.core.storage.CustomHeadersStore
import com.hermex.android.core.storage.CustomHttpHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeCustomHeadersStore(initial: List<CustomHttpHeader> = emptyList()) : CustomHeadersStore {
    private val _headers = MutableStateFlow(initial)
    override val headers: StateFlow<List<CustomHttpHeader>> = _headers.asStateFlow()
    override fun snapshot(): List<CustomHttpHeader> = _headers.value
    override suspend fun load(): List<CustomHttpHeader> = _headers.value
    override suspend fun save(headers: List<CustomHttpHeader>) { _headers.value = headers }
}

/** Verifies [NetworkModule]'s custom-headers interceptor -- both that it applies saved headers to
 * real outgoing requests, and that it can never override a blocked built-in. Uses a real
 * [MockWebServer] and inspects the actual [okhttp3.mockwebserver.RecordedRequest], since the
 * point is to confirm what OkHttp really put on the wire, not just call a function in isolation. */
class CustomHeadersInterceptorTest {
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

    private fun newModule(store: CustomHeadersStore) =
        NetworkModule(FakeCookieStore(), customHeadersStore = store, onUnauthorized = {})

    @Test
    fun `applies a valid custom header to outgoing REST requests`() = runTest {
        val store = FakeCustomHeadersStore(listOf(CustomHttpHeader(name = "X-Test-Header", value = "test123")))
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        val api = newModule(store).createApi(server.url("/").toString())

        safeApiCall { api.health() }

        val request = server.takeRequest()
        assertEquals("test123", request.getHeader("X-Test-Header"))
    }

    @Test
    fun `skips a header with an invalid name (space) and never sends it`() = runTest {
        val store = FakeCustomHeadersStore(listOf(CustomHttpHeader(name = "Bad Header", value = "should-not-send")))
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        val api = newModule(store).createApi(server.url("/").toString())

        safeApiCall { api.health() }

        val request = server.takeRequest()
        assertNull(request.getHeader("Bad Header"))
        assertTrue(request.headers.values("should-not-send").isEmpty()) // sanity: value never leaked as a header name either
    }

    @Test
    fun `does not override the app's own Content-Type on a POST request`() = runTest {
        val store = FakeCustomHeadersStore(listOf(CustomHttpHeader(name = "Content-Type", value = "text/plain")))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val api = newModule(store).createApi(server.url("/").toString())

        safeApiCall { api.login(LoginRequest("irrelevant")) }

        val request = server.takeRequest()
        val contentType = request.getHeader("Content-Type").orEmpty()
        assertTrue("expected a JSON content type, was: $contentType", contentType.contains("json"))
        assertTrue(!contentType.contains("text/plain"))
    }

    @Test
    fun `an empty header list is a true no-op and requests still succeed`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        val api = newModule(FakeCustomHeadersStore()).createApi(server.url("/").toString())

        val health = safeApiCall { api.health() }

        assertEquals("ok", health.status)
    }

    @Test
    fun `useServer repoints headers without rebuilding the module -- server A's header never reaches server B`() = runTest {
        val storeA = FakeCustomHeadersStore(listOf(CustomHttpHeader(name = "X-Server-A-Key", value = "a-secret")))
        val storeB = FakeCustomHeadersStore(listOf(CustomHttpHeader(name = "X-Server-B-Key", value = "b-secret")))
        val module = NetworkModule(FakeCookieStore(), customHeadersStore = storeA, onUnauthorized = {})
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

        // Same module/api-building path is reused for both servers -- only the active scope
        // changes, never the OkHttpClient/Retrofit machinery itself.
        val apiForA = module.createApi(server.url("/").toString())
        safeApiCall { apiForA.health() }
        val requestToA = server.takeRequest()
        assertEquals("a-secret", requestToA.getHeader("X-Server-A-Key"))

        module.useServer(FakeCookieStore(), storeB)
        val apiForB = module.createApi(server.url("/").toString())
        safeApiCall { apiForB.health() }
        val requestToB = server.takeRequest()

        assertNull("server A's header must never reach server B", requestToB.getHeader("X-Server-A-Key"))
        assertEquals("b-secret", requestToB.getHeader("X-Server-B-Key"))
    }

    @Test
    fun `the SSE client shares the same interceptor -- custom headers apply to raw streamed requests too`() {
        val store = FakeCustomHeadersStore(listOf(CustomHttpHeader(name = "X-Test-Header", value = "test123")))
        server.enqueue(MockResponse().setBody("event: message\ndata: {}\n\n"))
        val networkModule = newModule(store)

        // Bypasses Retrofit entirely, exactly like SseClient does -- a raw request through the
        // dedicated SSE OkHttpClient (see NetworkModule.sseClient).
        val request = Request.Builder().url(server.url("/api/chat/stream")).build()
        networkModule.sseClient.newCall(request).execute().close()

        val recorded = server.takeRequest()
        assertEquals("test123", recorded.getHeader("X-Test-Header"))
    }
}
