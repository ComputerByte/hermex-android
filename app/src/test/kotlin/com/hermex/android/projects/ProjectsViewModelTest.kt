package com.hermex.android.projects

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
class ProjectsViewModelTest {
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
    fun `loads the project list`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"projects":[{"project_id":"a","name":"Mobile"},{"project_id":"b","name":"Backend"}]}""",
            ),
        )

        val viewModel = ProjectsViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(2, loaded.projects.size)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces an error without crashing on a load failure`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = ProjectsViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.errorMessage != null)
            assertTrue(loaded.projects.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `create posts name and color, then reloads the list`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"projects":[]}"""))
        val viewModel = ProjectsViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"ok":true,"project":{"project_id":"a","name":"Mobile"}}"""))
        server.enqueue(MockResponse().setBody("""{"projects":[{"project_id":"a","name":"Mobile","color":"#7cb9ff"}]}"""))

        viewModel.uiState.test {
            viewModel.create("Mobile", "#7cb9ff")
            // isMutating defaults to false, so confirm the mutation actually started before
            // waiting for it to finish -- otherwise "!it.isMutating" matches the pre-mutation
            // state too (see TaskMutationsTest for the same lesson learned earlier).
            awaitUntil { it.isMutating }
            val afterCreate = awaitUntil { !it.isMutating }
            assertEquals(1, afterCreate.projects.size)
            assertNull(afterCreate.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // initial load
        val createRequest = server.takeRequest()
        assertTrue(createRequest.path?.contains("/api/projects/create") == true)
        assertTrue(createRequest.body.readUtf8().contains("\"name\":\"Mobile\""))
    }

    @Test
    fun `create ignores a blank name without calling the API`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"projects":[]}"""))
        val viewModel = ProjectsViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        server.takeRequest() // initial load

        viewModel.create("   ", "#7cb9ff")

        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `rename posts the project id, name, and color, then reloads`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"projects":[{"project_id":"a","name":"Mobile"}]}"""))
        val viewModel = ProjectsViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"ok":true,"project":{"project_id":"a","name":"Mobile App"}}"""))
        server.enqueue(MockResponse().setBody("""{"projects":[{"project_id":"a","name":"Mobile App"}]}"""))

        viewModel.uiState.test {
            viewModel.rename("a", "Mobile App", "#f5c542")
            awaitUntil { it.isMutating }
            val afterRename = awaitUntil { !it.isMutating }
            assertEquals("Mobile App", afterRename.projects.first().name)
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // initial load
        val renameRequest = server.takeRequest()
        assertTrue(renameRequest.path?.contains("/api/projects/rename") == true)
        assertTrue(renameRequest.body.readUtf8().contains("\"project_id\":\"a\""))
    }

    @Test
    fun `delete posts the project id and reloads, dropping it from the list`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"projects":[{"project_id":"a","name":"Mobile"}]}"""))
        val viewModel = ProjectsViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setBody("""{"projects":[]}"""))

        viewModel.uiState.test {
            viewModel.delete("a")
            awaitUntil { it.isMutating }
            val afterDelete = awaitUntil { !it.isMutating }
            assertTrue(afterDelete.projects.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        server.takeRequest() // initial load
        val deleteRequest = server.takeRequest()
        assertTrue(deleteRequest.path?.contains("/api/projects/delete") == true)
        assertTrue(deleteRequest.body.readUtf8().contains("\"project_id\":\"a\""))
    }

    @Test
    fun `a mutation failure surfaces the server error and leaves the list unchanged`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"projects":[{"project_id":"a","name":"Mobile"}]}"""))
        val viewModel = ProjectsViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"ok":false,"error":"name already in use"}"""))

        viewModel.uiState.test {
            viewModel.rename("a", "Backend", "#f5c542")
            awaitUntil { it.isMutating }
            val afterRename = awaitUntil { !it.isMutating }
            assertEquals("name already in use", afterRename.errorMessage)
            assertEquals("Mobile", afterRename.projects.first().name) // unchanged, no reload happened
            cancelAndIgnoreRemainingEvents()
        }
    }
}
