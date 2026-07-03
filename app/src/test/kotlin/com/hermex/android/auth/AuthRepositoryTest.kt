package com.hermex.android.auth

import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.storage.FakeServerStore
import com.hermex.android.core.storage.NoOpCustomHeadersStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var serverStore: FakeServerStore
    private lateinit var cookieStores: MutableMap<String, FakeCookieStore>
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        serverStore = FakeServerStore()
        cookieStores = mutableMapOf()
        val networkModule = NetworkModule(FakeCookieStore()) { repository.handleUnauthorized() }
        repository = AuthRepository(
            networkModule = networkModule,
            serverStore = serverStore,
            cookieStoreFactory = { id -> cookieStores.getOrPut(id) { FakeCookieStore() } },
            customHeadersStoreFactory = { NoOpCustomHeadersStore },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl() = server.url("/").toString()

    @Test
    fun `restoreSavedServer with nothing configured sets Unconfigured and clears isRestoring`() = runTest {
        repository.restoreSavedServer()

        assertEquals(AuthState.Unconfigured, repository.state.value)
        assertEquals(false, repository.isRestoring.value)
    }

    @Test
    fun `restoreSavedServer with an active server optimistically sets LoggedIn`() = runTest {
        serverStore = FakeServerStore("https://hermes.example.com/")
        val networkModule = NetworkModule(FakeCookieStore()) { repository.handleUnauthorized() }
        repository = AuthRepository(networkModule, serverStore)

        repository.restoreSavedServer()

        val state = repository.state.value as AuthState.LoggedIn
        assertEquals("https://hermes.example.com/", state.serverUrl)
        assertEquals(serverStore.activeServerSnapshot()!!.id, state.serverId)
    }

    @Test
    fun `login when auth is disabled succeeds, persists the server, and calls no login endpoint`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":false}"""))

        val outcome = repository.login(baseUrl(), password = "")

        assertTrue(outcome is LoginOutcome.Success)
        assertTrue(repository.state.value is AuthState.LoggedIn)
        assertEquals(2, server.requestCount)
        assertEquals(1, serverStore.state.value.servers.size)
        assertEquals(baseUrl(), serverStore.activeServerSnapshot()?.baseUrl)
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
        assertEquals(1, serverStore.state.value.servers.size)
    }

    @Test
    fun `login with the wrong password fails and never persists a server`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":true,"password_auth_enabled":true}"""))
        server.enqueue(MockResponse().setResponseCode(401))

        val outcome = repository.login(baseUrl(), password = "wrong-password")

        assertTrue(outcome is LoginOutcome.Failed)
        assertTrue(repository.state.value !is AuthState.LoggedIn)
        assertTrue(serverStore.state.value.servers.isEmpty())
    }

    @Test
    fun `login against a passkey-only server returns PasskeyOnly and never attempts password login`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":true,"password_auth_enabled":false}"""))

        val outcome = repository.login(baseUrl(), password = "")

        assertTrue(outcome is LoginOutcome.PasskeyOnly)
        assertEquals(2, server.requestCount) // never called /api/auth/login
        assertTrue(serverStore.state.value.servers.isEmpty())
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
    fun `logging in again to an already-configured server reuses its id instead of duplicating it`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":false}"""))
        repository.login(baseUrl(), password = "")
        val firstId = serverStore.activeServerSnapshot()!!.id

        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":false}"""))
        repository.login(baseUrl(), password = "")

        assertEquals(1, serverStore.state.value.servers.size)
        assertEquals(firstId, serverStore.activeServerSnapshot()!!.id)
        assertEquals(firstId, (repository.state.value as AuthState.LoggedIn).serverId)
    }

    @Test
    fun `a failed login to a new server never disturbs the currently active server's cookies`() = runTest {
        // Server A is already configured, active, and logged in.
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":false}"""))
        repository.login(baseUrl(), password = "")
        val serverAId = serverStore.activeServerSnapshot()!!.id
        cookieStores[serverAId]!!.stored = """[{"name":"session","value":"server-a-session","domain":"x","path":"/","expiresAt":9999999999999,"secure":false,"httpOnly":false}]"""

        // Attempting to log into an unreachable second server fails.
        val deadServer = MockWebServer().apply { start() }
        val deadUrl = deadServer.url("/").toString()
        deadServer.shutdown()

        val outcome = repository.login(deadUrl, password = "")

        assertTrue(outcome is LoginOutcome.Failed)
        assertEquals(serverAId, (repository.state.value as AuthState.LoggedIn).serverId)
        assertTrue(
            "server A's cookie must be untouched by the failed attempt at a different server",
            cookieStores[serverAId]!!.stored?.contains("server-a-session") == true,
        )
        assertEquals(1, serverStore.state.value.servers.size) // the failed candidate was never persisted
    }

    @Test
    fun `handleUnauthorized demotes LoggedIn to LoggedOut while keeping the server`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":false}"""))
        repository.login(baseUrl(), password = "")
        val loggedIn = repository.state.value as AuthState.LoggedIn

        repository.handleUnauthorized()

        assertEquals(AuthState.LoggedOut(loggedIn.serverId, loggedIn.serverUrl), repository.state.value)
    }

    @Test
    fun `handleUnauthorized while Unconfigured is a no-op`() = runTest {
        repository.restoreSavedServer() // nothing configured -> Unconfigured

        repository.handleUnauthorized()

        assertEquals(AuthState.Unconfigured, repository.state.value)
    }

    @Test
    fun `signOut clears the session and demotes to LoggedOut, keeping the server configured`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":false}"""))
        repository.login(baseUrl(), password = "")
        val loggedIn = repository.state.value as AuthState.LoggedIn
        server.enqueue(MockResponse().setBody("""{"ok":true}""")) // /api/auth/logout

        repository.signOut()

        assertEquals(AuthState.LoggedOut(loggedIn.serverId, loggedIn.serverUrl), repository.state.value)
        assertEquals(1, serverStore.state.value.servers.size) // still configured, just logged out
    }

    @Test
    fun `switchActiveServer repoints networking and state at the newly active server`() = runTest {
        val first = serverStore.addServer("A", "https://a.example.com/")
        val second = serverStore.addServer("B", "https://b.example.com/")
        repository.restoreSavedServer()
        assertEquals(first.id, (repository.state.value as AuthState.LoggedIn).serverId)

        repository.switchActiveServer(second.id)

        val state = repository.state.value as AuthState.LoggedIn
        assertEquals(second.id, state.serverId)
        assertEquals("https://b.example.com/", state.serverUrl)
        assertEquals(second.id, serverStore.state.value.activeServerId)
    }

    @Test
    fun `forgetServer removes the server and clears its cookies`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":false}"""))
        repository.login(baseUrl(), password = "")
        val id = serverStore.activeServerSnapshot()!!.id
        cookieStores[id]!!.stored = "some-cookie-blob"

        repository.forgetServer(id)

        assertTrue(serverStore.state.value.servers.isEmpty())
        assertEquals(AuthState.Unconfigured, repository.state.value)
        assertNull(cookieStores[id]!!.stored)
    }

    @Test
    fun `forgetServer on the active server falls back to another configured server`() = runTest {
        val first = serverStore.addServer("A", "https://a.example.com/")
        val second = serverStore.addServer("B", "https://b.example.com/")
        repository.restoreSavedServer()

        repository.forgetServer(first.id)

        val state = repository.state.value as AuthState.LoggedIn
        assertEquals(second.id, state.serverId)
        assertEquals(listOf(second.id), serverStore.state.value.servers.map { it.id })
    }

    @Test
    fun `forgetServer invokes onServerForgotten so other server-scoped stores can clean up too`() = runTest {
        val forgottenIds = mutableListOf<String>()
        val networkModule = NetworkModule(FakeCookieStore()) { repository.handleUnauthorized() }
        repository = AuthRepository(
            networkModule = networkModule,
            serverStore = serverStore,
            cookieStoreFactory = { id -> cookieStores.getOrPut(id) { FakeCookieStore() } },
            customHeadersStoreFactory = { NoOpCustomHeadersStore },
            onServerForgotten = { id -> forgottenIds.add(id) },
        )
        val config = serverStore.addServer("A", "https://a.example.com/")

        repository.forgetServer(config.id)

        assertEquals(listOf(config.id), forgottenIds)
    }
}
