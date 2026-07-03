package com.hermex.android.profiles

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
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

private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var item = awaitItem()
    while (!predicate(item)) item = awaitItem()
    return item
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
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
    fun `loads the profile list and resolves the active profile`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"active":"work","profiles":[{"name":"default"},{"name":"work"}]}""",
            ),
        )

        val viewModel = ProfilesViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(2, loaded.profiles.size)
            assertEquals("work", loaded.activeName)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces an error without crashing on a load failure`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = ProfilesViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.errorMessage != null)
            assertTrue(loaded.profiles.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switchTo posts the profile name and updates the active profile from the response`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"active":"default","profiles":[{"name":"default"},{"name":"work"}]}"""))
        val viewModel = ProfilesViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"active":"work","profiles":[{"name":"default"},{"name":"work"}]}"""))

        viewModel.uiState.test {
            viewModel.switchTo("work")
            // switchingTo defaults to null, so confirm the switch actually started before waiting
            // for it to finish -- otherwise "switchingTo == null" matches the pre-switch state too.
            awaitUntil { it.switchingTo == "work" }
            val afterSwitch = awaitUntil { it.switchingTo == null }
            assertEquals("work", afterSwitch.activeName)
            assertNull(afterSwitch.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // initial load
        val switchRequest = server.takeRequest()
        assertTrue(switchRequest.path?.contains("/api/profile/switch") == true)
        assertTrue(switchRequest.body.readUtf8().contains("\"name\":\"work\""))
    }

    @Test
    fun `switchTo is a no-op when the requested profile is already active`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"active":"default","profiles":[{"name":"default"}]}"""))
        val viewModel = ProfilesViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        server.takeRequest() // drain initial load

        viewModel.switchTo("default")

        assertEquals(0, server.requestCount - 1)
    }

    @Test
    fun `switchTo surfaces a server-side error and leaves the previous active profile in place`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"active":"default","profiles":[{"name":"default"},{"name":"work"}]}"""))
        val viewModel = ProfilesViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"error":"profile not found"}"""))

        viewModel.uiState.test {
            viewModel.switchTo("work")
            awaitUntil { it.switchingTo == "work" }
            val afterSwitch = awaitUntil { it.switchingTo == null }
            assertEquals("profile not found", afterSwitch.errorMessage)
            assertEquals("default", afterSwitch.activeName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filteredProfiles filters by display name, case-insensitively`() {
        val state = ProfilesUiState(
            profiles = listOf(
                com.hermex.android.core.network.dto.ProfileSummary(name = "Default"),
                com.hermex.android.core.network.dto.ProfileSummary(name = "Work"),
            ),
            searchQuery = "wo",
        )
        assertEquals(listOf("Work"), state.filteredProfiles.map { it.displayName })
    }
}
