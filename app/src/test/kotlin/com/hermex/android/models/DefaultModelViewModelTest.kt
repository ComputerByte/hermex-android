package com.hermex.android.models

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.dto.ModelCatalogOption
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
class DefaultModelViewModelTest {
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
    fun `loads the model catalog, the current default, and overlays live models`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"groups":[{"provider_id":"openai","models":[{"id":"gpt-5.5"}]}],"default_model":"gpt-5.5"}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody("""{"provider":"openai","models":[{"id":"gpt-5.5"},{"id":"gpt-5.5-mini"}]}"""),
        )

        val viewModel = DefaultModelViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals("gpt-5.5", loaded.defaultModelId)
            assertEquals(1, loaded.groups.size)
            // the live overlay landed (2 models for openai instead of the cached 1)
            assertEquals(2, loaded.groups.first().models.size)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed live overlay is silently ignored, keeping the cached catalog`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"groups":[{"provider_id":"openai","models":[{"id":"gpt-5.5"}]}],"default_model":"gpt-5.5"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = DefaultModelViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(1, loaded.groups.first().models.size)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces an error without crashing when the catalog load fails`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = DefaultModelViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.errorMessage != null)
            assertTrue(loaded.groups.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectModel posts the model id and updates defaultModelId from the response`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"groups":[{"provider_id":"openai","models":[{"id":"a"},{"id":"b"}]}],"default_model":"a"}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"provider":"openai","models":[]}""")) // live overlay: empty, ignored
        val viewModel = DefaultModelViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"ok":true,"model":"b"}"""))

        viewModel.uiState.test {
            viewModel.selectModel(ModelCatalogOption(id = "b", displayName = "b", providerId = "openai"))
            // savingModelId defaults to null, so confirm the save actually started before waiting
            // for it to finish -- otherwise "savingModelId == null" matches the pre-save state too.
            awaitUntil { it.savingModelId == "b" }
            val afterSave = awaitUntil { it.savingModelId == null }
            assertEquals("b", afterSave.defaultModelId)
            assertNull(afterSave.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // models
        server.takeRequest() // models/live
        val setDefaultRequest = server.takeRequest()
        assertTrue(setDefaultRequest.path?.contains("/api/default-model") == true)
        assertTrue(setDefaultRequest.body.readUtf8().contains("\"model\":\"b\""))
    }

    @Test
    fun `selectModel is a no-op when the option is already the default`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"groups":[{"provider_id":"openai","models":[{"id":"a"}]}],"default_model":"a"}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"provider":"openai","models":[]}"""))
        val viewModel = DefaultModelViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        server.takeRequest()
        server.takeRequest()

        viewModel.selectModel(ModelCatalogOption(id = "a", displayName = "a", providerId = "openai"))

        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `selectModel surfaces a not-confirmed error when the server returns ok false`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"groups":[{"provider_id":"openai","models":[{"id":"a"},{"id":"b"}]}],"default_model":"a"}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"provider":"openai","models":[]}"""))
        val viewModel = DefaultModelViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"ok":false}"""))

        viewModel.uiState.test {
            viewModel.selectModel(ModelCatalogOption(id = "b", displayName = "b", providerId = "openai"))
            awaitUntil { it.savingModelId == "b" }
            val afterSave = awaitUntil { it.savingModelId == null }
            assertEquals("a", afterSave.defaultModelId) // unchanged
            assertEquals("The server did not confirm the change.", afterSave.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
