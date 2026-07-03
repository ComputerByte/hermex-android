package com.hermex.android.skills

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeServerStore(initial: String?) : ServerStore {
    var stored: String? = initial
    override suspend fun save(serverUrl: String) { stored = serverUrl }
    override suspend fun load(): String? = stored
    override suspend fun clear() { stored = null }
}

private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var item = awaitItem()
    while (!predicate(item)) item = awaitItem()
    return item
}

@OptIn(ExperimentalCoroutinesApi::class)
class SkillsViewModelTest {
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
    fun `SkillsViewModel loads and groups nothing special -- just exposes the flat list`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"skills":[{"name":"a","category":"dev"},{"name":"b","category":"writing"}]}""",
            ),
        )

        val viewModel = SkillsViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(2, loaded.skills.size)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SkillsViewModel surfaces an error without crashing on failure`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = SkillsViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.errorMessage != null)
            assertTrue(loaded.skills.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SkillDetailViewModel loads content and linked files, falling back to the requested name`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"content":"Some skill content","linked_files":["a.py","b.py"]}""",
            ),
        )

        val viewModel = SkillDetailViewModel("web-search", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals("web-search", loaded.name) // response omitted "name" -- falls back to the requested name
            assertEquals("Some skill content", loaded.content)
            assertEquals(listOf("a.py", "b.py"), loaded.linkedFiles)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
