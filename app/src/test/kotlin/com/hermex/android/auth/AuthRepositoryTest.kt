package com.hermex.android.auth

import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.storage.ServerStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeServerStore : ServerStore {
    var stored: String? = null
    override suspend fun save(serverUrl: String) { stored = serverUrl }
    override suspend fun load(): String? = stored
    override suspend fun clear() { stored = null }
}

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var serverStore: FakeServerStore
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        serverStore = FakeServerStore()
        val networkModule = NetworkModule(FakeCookieStore()) { repository.handleUnauthorized() }
        repository = AuthRepository(networkModule, serverStore)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl() = server.url("/").toString()

    @Test
    fun `restoreSavedServer with nothing saved sets Unconfigured and clears isRestoring`() = runTest {
        repository.restoreSavedServer()

        assertEquals(AuthState.Unconfigured, repository.state.value)
        assertEquals(false, repository.isRestoring.value)
    }

    @Test
    fun `restoreSavedServer with a saved server optimistically sets LoggedIn`() = runTest {
        serverStore.stored = "https://hermes.example.com/"

        repository.restoreSavedServer()

        assertEquals(AuthState.LoggedIn("https://hermes.example.com/"), repository.state.value)
    }

    @Test
    fun `login when auth is disabled succeeds without ever calling the login endpoint`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":false}"""))

        val outcome = repository.login(baseUrl(), password = "")

        assertTrue(outcome is LoginOutcome.Success)
        assertTrue(repository.state.value is AuthState.LoggedIn)
        assertEquals(2, server.requestCount)
        assertTrue(serverStore.stored != null)
    }

    @Test
    fun `login when auth is enabled with the correct password succeeds and persists the server`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":true,"password_auth_enabled":true}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val outcome = repository.login(baseUrl(), password = "correct-horse")

        assertTrue(outcome is LoginOutcome.Success)
        assertTrue(repository.state.value is AuthState.LoggedIn)
        assertEquals(3, server.requestCount)
        assertTrue(serverStore.stored != null)
    }

    @Test
    fun `login with the wrong password fails and never persists the server`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":true,"password_auth_enabled":true}"""))
        server.enqueue(MockResponse().setResponseCode(401))

        val outcome = repository.login(baseUrl(), password = "wrong-password")

        assertTrue(outcome is LoginOutcome.Failed)
        assertTrue(repository.state.value !is AuthState.LoggedIn)
        assertNull(serverStore.stored)
    }

    @Test
    fun `login against a passkey-only server returns PasskeyOnly and never attempts password login`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":true,"password_auth_enabled":false}"""))

        val outcome = repository.login(baseUrl(), password = "")

        assertTrue(outcome is LoginOutcome.PasskeyOnly)
        assertEquals(2, server.requestCount) // never called /api/auth/login
        assertNull(serverStore.stored)
    }

    @Test
    fun `login with a blank password when the server requires one fails locally`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":true,"password_auth_enabled":true}"""))

        val outcome = repository.login(baseUrl(), password = "")

        assertTrue(outcome is LoginOutcome.Failed)
        assertEquals(2, server.requestCount) // never called /api/auth/login with a blank password
    }

    @Test
    fun `login with an empty URL fails without making any network call`() = runTest {
        val outcome = repository.login("", password = "")

        assertTrue(outcome is LoginOutcome.Failed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `login against an unreachable server fails gracefully`() = runTest {
        val deadUrl = baseUrl()
        server.shutdown()

        val outcome = repository.login(deadUrl, password = "")

        assertTrue(outcome is LoginOutcome.Failed)
    }

    @Test
    fun `handleUnauthorized demotes LoggedIn to LoggedOut while keeping the server URL`() = runTest {
        serverStore.stored = "https://hermes.example.com/"
        repository.restoreSavedServer()
        assertTrue(repository.state.value is AuthState.LoggedIn)

        repository.handleUnauthorized()

        assertEquals(AuthState.LoggedOut("https://hermes.example.com/"), repository.state.value)
    }

    @Test
    fun `handleUnauthorized while Unconfigured is a no-op`() = runTest {
        repository.restoreSavedServer() // nothing saved -> Unconfigured

        repository.handleUnauthorized()

        assertEquals(AuthState.Unconfigured, repository.state.value)
    }

    @Test
    fun `signOut clears the saved server and returns to Unconfigured`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":false}"""))
        repository.login(baseUrl(), password = "")
        server.enqueue(MockResponse().setBody("""{"ok":true}""")) // /api/auth/logout

        repository.signOut()

        assertEquals(AuthState.Unconfigured, repository.state.value)
        assertNull(serverStore.stored)
    }
}
