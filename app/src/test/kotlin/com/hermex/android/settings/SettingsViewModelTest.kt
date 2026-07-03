package com.hermex.android.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.auth.AuthState
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.storage.ChatPreferencesStore
import com.hermex.android.core.storage.CustomHttpHeader
import com.hermex.android.core.storage.FakeServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeChatPreferencesStore(private var expandThinkingByDefault: Boolean = false) : ChatPreferencesStore {
    override suspend fun loadExpandThinkingByDefault(): Boolean = expandThinkingByDefault
    override suspend fun setExpandThinkingByDefault(value: Boolean) { expandThinkingByDefault = value }
}

private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var item = awaitItem()
    while (!predicate(item)) item = awaitItem()
    return item
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private suspend fun loggedInRepository(): AuthRepository {
        lateinit var repo: AuthRepository
        val networkModule = NetworkModule(FakeCookieStore()) { repo.handleUnauthorized() }
        repo = AuthRepository(networkModule, FakeServerStore(server.url("/").toString()))
        repo.restoreSavedServer()
        return repo
    }

    @Test
    fun `loads the server url, version, and default model`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"version":"v0.51.766"}""")) // GET /api/settings
        server.enqueue(MockResponse().setBody("""{"default_model":"gpt-5.5"}""")) // GET /api/models

        val viewModel = SettingsViewModel(repo, FakeChatPreferencesStore(), FakeCustomHeadersStore())

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(server.url("/").toString(), loaded.serverUrl)
            assertEquals("v0.51.766", loaded.serverVersion)
            assertEquals("gpt-5.5", loaded.defaultModel)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces an error without crashing when the server settings call fails`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500)) // GET /api/settings
        server.enqueue(MockResponse().setBody("""{"default_model":"gpt-5.5"}""")) // GET /api/models

        val viewModel = SettingsViewModel(repo, FakeChatPreferencesStore(), FakeCustomHeadersStore())

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.errorMessage != null)
            // the server URL still shows even though the version lookup failed, and the
            // independent /api/models call still succeeds
            assertEquals(server.url("/").toString(), loaded.serverUrl)
            assertEquals("gpt-5.5", loaded.defaultModel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signOut clears the session and demotes to LoggedOut, keeping the server configured`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"version":"v0.51.766"}""")) // GET /api/settings
        server.enqueue(MockResponse().setBody("""{"default_model":"gpt-5.5"}""")) // GET /api/models
        val viewModel = SettingsViewModel(repo, FakeChatPreferencesStore(), FakeCustomHeadersStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"ok":true}""")) // /api/auth/logout

        // isSigningOut is never flipped back to false (HermexNavGraph unmounts this screen once
        // AuthRepository.state leaves LoggedIn), so assert completion on the repository's own
        // state flow rather than waiting on SettingsUiState for a signal that never comes.
        repo.state.test {
            val loggedIn = awaitItem() as AuthState.LoggedIn
            assertEquals(server.url("/").toString(), loggedIn.serverUrl)
            viewModel.signOut()
            // Multi-server: signing out only clears this server's session -- it stays configured
            // (still shows in the server list) rather than being fully forgotten, mirroring how a
            // 401 already demotes to LoggedOut without discarding the server URL.
            assertEquals(AuthState.LoggedOut(loggedIn.serverId, loggedIn.serverUrl), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load reads the persisted expandThinkingByDefault preference`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"version":"v0.51.766"}"""))
        server.enqueue(MockResponse().setBody("""{"default_model":"gpt-5.5"}"""))

        val viewModel = SettingsViewModel(repo, FakeChatPreferencesStore(expandThinkingByDefault = true), FakeCustomHeadersStore())

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.expandThinkingByDefault)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setExpandThinkingByDefault updates state immediately and persists to the store`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"version":"v0.51.766"}"""))
        server.enqueue(MockResponse().setBody("""{"default_model":"gpt-5.5"}"""))
        val store = FakeChatPreferencesStore()
        val viewModel = SettingsViewModel(repo, store, FakeCustomHeadersStore())
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        viewModel.uiState.test {
            viewModel.setExpandThinkingByDefault(true)
            val afterToggle = awaitUntil { it.expandThinkingByDefault }
            assertTrue(afterToggle.expandThinkingByDefault)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(store.loadExpandThinkingByDefault())
    }

    @Test
    fun `load reflects the saved custom header count`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"version":"v0.51.766"}"""))
        server.enqueue(MockResponse().setBody("""{"default_model":"gpt-5.5"}"""))
        val headersStore = FakeCustomHeadersStore(
            listOf(CustomHttpHeader(name = "X-Api-Key", value = "secret"), CustomHttpHeader(name = "Authorization", value = "Bearer x")),
        )

        val viewModel = SettingsViewModel(repo, FakeChatPreferencesStore(), headersStore)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(2, loaded.customHeaderCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customHeaderCount is 0 when no headers are saved`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"version":"v0.51.766"}"""))
        server.enqueue(MockResponse().setBody("""{"default_model":"gpt-5.5"}"""))

        val viewModel = SettingsViewModel(repo, FakeChatPreferencesStore(), FakeCustomHeadersStore())

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(0, loaded.customHeaderCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
