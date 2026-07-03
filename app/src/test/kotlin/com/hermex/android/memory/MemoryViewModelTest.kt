package com.hermex.android.memory

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
class MemoryViewModelTest {
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
    fun `loads all three sections with their content and mtime`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"memory":"remembers stuff","user":"likes kotlin","soul":null,"memory_mtime":1770000000,"user_mtime":1770000100}""",
            ),
        )

        val viewModel = MemoryViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals("remembers stuff", loaded.memorySection?.content)
            assertEquals(1770000000.0, loaded.memorySection?.mtime)
            assertEquals("likes kotlin", loaded.userSection?.content)
            assertNull(loaded.soulSection?.content)
            assertNull(loaded.soulSection?.mtime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty response (all fields absent) does not crash -- every section is just empty`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("{}"))

        val viewModel = MemoryViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertNull(loaded.memorySection?.content)
            assertNull(loaded.userSection?.content)
            assertNull(loaded.soulSection?.content)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a server error surfaces as errorMessage without crashing`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = MemoryViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.errorMessage != null)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
