package com.hermex.android.sessions

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.auth.AuthState
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.storage.FakeAppearancePreferencesStore
import com.hermex.android.core.storage.FakeServerStore
import com.hermex.android.core.storage.HeaderLogoColor
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

/**
 * Retrofit/OkHttp calls resume on OkHttp's own real background thread (they are not part of
 * kotlinx-coroutines-test's virtual clock), so `advanceUntilIdle()` on a `StandardTestDispatcher`
 * cannot reliably wait for them -- it can return before the real network round trip lands. This
 * helper genuinely suspends (via Turbine's real `Channel`) until a state matching [predicate]
 * arrives, which correctly waits regardless of which thread produced it.
 */
private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var item = awaitItem()
    while (!predicate(item)) item = awaitItem()
    return item
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionListViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        // Unconfined (not Standard): resumptions arriving from OkHttp's real background thread
        // run eagerly rather than sitting in a virtual-time queue that only advances on demand.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    /** Logs a fresh [AuthRepository] into [server] optimistically (mirrors app startup restoring
     * a previously-saved server) so [SessionListViewModel] has a working `apiForActiveServer()`. */
    private suspend fun loggedInRepository(): AuthRepository {
        lateinit var repo: AuthRepository
        val networkModule = NetworkModule(FakeCookieStore()) { repo.handleUnauthorized() }
        repo = AuthRepository(networkModule, FakeServerStore(server.url("/").toString()))
        repo.restoreSavedServer()
        return repo
    }

    @Test
    fun `load populates sessions on success`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"sessions":[{"session_id":"a","title":"First"},{"session_id":"b","title":"Second"}]}""",
            ),
        )

        val viewModel = SessionListViewModel(authRepository)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(2, loaded.sessions.size)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load with an empty sessions array yields an empty list, not an error`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"sessions":[]}"""))

        val viewModel = SessionListViewModel(authRepository)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.sessions.isEmpty())
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a 401 while loading routes auth state back to LoggedOut, keeping the server URL`() = runTest {
        authRepository = loggedInRepository()
        val loggedIn = authRepository.state.value as AuthState.LoggedIn
        server.enqueue(MockResponse().setResponseCode(401))

        val viewModel = SessionListViewModel(authRepository)

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(AuthState.LoggedOut(loggedIn.serverId, loggedIn.serverUrl), authRepository.state.value)
    }

    @Test
    fun `refresh reloads sessions and clears isRefreshing when done`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"sessions":[]}"""))
        val viewModel = SessionListViewModel(authRepository)

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }

            server.enqueue(MockResponse().setBody("""{"sessions":[{"session_id":"a","title":"New"}]}"""))
            viewModel.refresh()
            val refreshed = awaitUntil { !it.isRefreshing }

            assertEquals(1, refreshed.sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createSession invokes onCreated with the new session id`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"sessions":[]}"""))
        val viewModel = SessionListViewModel(authRepository)

        var createdId: String? = null
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }

            server.enqueue(MockResponse().setBody("""{"session":{"session_id":"new-id"}}"""))
            viewModel.createSession { createdId = it }
            awaitUntil { !it.isCreatingSession }

            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("new-id", createdId)
    }

    @Test
    fun `createSession failure surfaces an error message and does not crash`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"sessions":[]}"""))
        val viewModel = SessionListViewModel(authRepository)

        var createdId: String? = null
        viewModel.uiState.test {
            awaitUntil { !it.isLoading }

            server.enqueue(MockResponse().setResponseCode(500))
            viewModel.createSession { createdId = it }
            val afterCreate = awaitUntil { !it.isCreatingSession }

            assertTrue(afterCreate.errorMessage != null)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(createdId)
    }

    @Test
    fun `onSearchQueryChanged filters sessions by title, case-insensitively`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"sessions":[{"session_id":"a","title":"Fix login bug"},{"session_id":"b","title":"Refactor sessions list"}]}""",
            ),
        )
        val viewModel = SessionListViewModel(authRepository)

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onSearchQueryChanged("login")
            val filtered = awaitUntil { it.searchQuery == "login" }
            assertEquals(1, filtered.filteredSessions.size)
            assertEquals("Fix login bug", filtered.filteredSessions.first().title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty or blank search query returns every session unfiltered`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"sessions":[{"session_id":"a","title":"First"},{"session_id":"b","title":"Second"}]}""",
            ),
        )
        val viewModel = SessionListViewModel(authRepository)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(2, loaded.filteredSessions.size)
            viewModel.onSearchQueryChanged("   ")
            val blank = awaitUntil { it.searchQuery == "   " }
            assertEquals(2, blank.filteredSessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a session with no title never matches a non-blank search`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"sessions":[{"session_id":"a"}]}"""))
        val viewModel = SessionListViewModel(authRepository)

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            viewModel.onSearchQueryChanged("anything")
            val filtered = awaitUntil { it.searchQuery == "anything" }
            assertTrue(filtered.filteredSessions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads the saved header logo color at init`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"sessions":[]}"""))

        val viewModel = SessionListViewModel(authRepository, FakeAppearancePreferencesStore(HeaderLogoColor.GREEN))

        viewModel.uiState.test {
            val loaded = awaitUntil { it.headerLogoColor == HeaderLogoColor.GREEN }
            assertEquals(HeaderLogoColor.GREEN, loaded.headerLogoColor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `headerLogoColor defaults to DEFAULT when nothing is saved`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"sessions":[]}"""))

        val viewModel = SessionListViewModel(authRepository)

        viewModel.uiState.test {
            awaitUntil { !it.isLoading }
            assertEquals(HeaderLogoColor.DEFAULT, viewModel.uiState.value.headerLogoColor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadHeaderLogoColor re-reads the preference without re-fetching sessions`() = runTest {
        authRepository = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"sessions":[]}"""))
        val store = FakeAppearancePreferencesStore()
        val viewModel = SessionListViewModel(authRepository, store)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        store.stored = HeaderLogoColor.PINK
        viewModel.uiState.test {
            viewModel.loadHeaderLogoColor()
            val updated = awaitUntil { it.headerLogoColor == HeaderLogoColor.PINK }
            assertEquals(HeaderLogoColor.PINK, updated.headerLogoColor)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, server.requestCount) // only the one /api/sessions call from init's load()
    }
}
