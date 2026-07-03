package com.hermex.android.onboarding

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.storage.ServerStore
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
import org.junit.Before
import org.junit.Test

/** See [com.hermex.android.sessions.SessionListViewModelTest] for why this pattern is required:
 * Retrofit/OkHttp calls resume on a real background thread, not kotlinx-coroutines-test's
 * virtual clock, so reading `.value` right after a non-suspend call that triggers real I/O is
 * unreliable -- this genuinely suspends until a matching state actually arrives. */
private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var item = awaitItem()
    while (!predicate(item)) item = awaitItem()
    return item
}

private class FakeServerStore(initial: String?) : ServerStore {
    var stored: String? = initial
    override suspend fun save(serverUrl: String) { stored = serverUrl }
    override suspend fun load(): String? = stored
    override suspend fun clear() { stored = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
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

    private suspend fun authRepository(savedServerUrl: String?): AuthRepository {
        lateinit var repo: AuthRepository
        val networkModule = NetworkModule(FakeCookieStore()) { repo.handleUnauthorized() }
        repo = AuthRepository(networkModule, FakeServerStore(savedServerUrl))
        repo.restoreSavedServer()
        return repo
    }

    @Test
    fun `pre-fills the server URL from a session-expired (LoggedOut) AuthState`() = runTest {
        val repo = authRepository(savedServerUrl = server.url("/").toString())
        // Simulate the real session-expiry path: an authenticated call 401s, demoting
        // LoggedIn -> LoggedOut(serverUrl) while keeping the URL, exactly as the "stale cookie"
        // scenario does in production.
        repo.handleUnauthorized()

        val viewModel = OnboardingViewModel(repo)

        assertEquals(server.url("/").toString(), viewModel.uiState.value.serverUrlInput)
    }

    @Test
    fun `starts with an empty server URL when there is nothing to restore (Unconfigured)`() = runTest {
        val repo = authRepository(savedServerUrl = null)

        val viewModel = OnboardingViewModel(repo)

        assertEquals("", viewModel.uiState.value.serverUrlInput)
    }

    @Test
    fun `does NOT pre-fill from a still-LoggedIn AuthState -- only session expiry should`() = runTest {
        // If restoreSavedServer() alone (no 401) put the app in LoggedIn, Onboarding should
        // never even be shown by the nav graph -- but if it somehow is, don't misuse the field.
        val repo = authRepository(savedServerUrl = server.url("/").toString())

        val viewModel = OnboardingViewModel(repo)

        assertEquals("", viewModel.uiState.value.serverUrlInput)
    }

    @Test
    fun `onServerUrlChanged updates the input and resets test-connection state`() = runTest {
        val repo = authRepository(savedServerUrl = null)
        val viewModel = OnboardingViewModel(repo)

        viewModel.onServerUrlChanged("hermes.example.com")

        val state = viewModel.uiState.value
        assertEquals("hermes.example.com", state.serverUrlInput)
        assertEquals(false, state.hasTestedConnection)
        assertEquals(false, state.requiresPassword)
    }

    @Test
    fun `login when auth is disabled succeeds and clears the loading flag`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"auth_enabled":false}"""))
        val repo = authRepository(savedServerUrl = null)
        val viewModel = OnboardingViewModel(repo)
        viewModel.onServerUrlChanged(server.url("/").toString())

        viewModel.uiState.test {
            awaitItem() // current state before login() is even called
            viewModel.login()
            val loggingIn = awaitUntil { it.isLoggingIn }
            val finalState = awaitUntil { !it.isLoggingIn }

            assertEquals(true, loggingIn.isLoggingIn) // sanity: it really did start
            assertEquals(null, finalState.errorMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
