package com.hermex.android.tasks

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
import okhttp3.mockwebserver.RecordedRequest
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
class TaskMutationsTest {
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

    private fun enqueueInitialLoad(state: String? = null) {
        val stateField = if (state != null) ""","state":"$state"""" else ""
        val jobJson = """{"job_id":"a","name":"Daily report"$stateField}"""
        server.enqueue(MockResponse().setBody("""{"jobs":[$jobJson]}"""))
        server.enqueue(MockResponse().setBody("""{"job_id":"a","running":false}"""))
        server.enqueue(MockResponse().setBody("""{"job_id":"a","outputs":[]}"""))
    }

    @Test
    fun `runNow posts to crons run with the job id and reloads afterward`() = runTest {
        val repo = loggedInRepository()
        enqueueInitialLoad()
        val viewModel = TaskDetailViewModel("a", repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }

        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        enqueueInitialLoad() // the reload after a successful mutation

        viewModel.uiState.test {
            viewModel.runNow()
            // isMutating defaults to false, so confirm the mutation actually started before
            // waiting for it to finish -- otherwise "!it.isMutating" matches the pre-mutation
            // state too (see SessionListViewModelTest for the same lesson learned earlier).
            awaitUntil { it.isMutating }
            val afterMutation = awaitUntil { !it.isMutating }
            assertNull(afterMutation.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }

        // 3 initial load requests + 1 run + 3 reload requests = 7; the run request is index 3.
        val runRequest: RecordedRequest = server.takeRequest() // session load #1
        server.takeRequest() // status
        server.takeRequest() // output
        val actualRunRequest = server.takeRequest()
        assertTrue(actualRunRequest.path?.contains("/api/crons/run") == true)
        assertTrue(actualRunRequest.body.readUtf8().contains("\"job_id\":\"a\""))
        assertTrue(runRequest.path?.contains("/api/crons") == true)
    }

    @Test
    fun `togglePauseResume pauses an active job`() = runTest {
        val repo = loggedInRepository()
        enqueueInitialLoad() // no state -> ACTIVE
        val viewModel = TaskDetailViewModel("a", repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        repeat(3) { server.takeRequest() } // drain initial load requests

        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        enqueueInitialLoad("paused")

        viewModel.uiState.test {
            viewModel.togglePauseResume()
            // isMutating defaults to false, so confirm the mutation actually started before
            // waiting for it to finish -- otherwise "!it.isMutating" matches the pre-mutation
            // state too (see SessionListViewModelTest for the same lesson learned earlier).
            awaitUntil { it.isMutating }
            val afterMutation = awaitUntil { !it.isMutating }
            assertEquals(com.hermex.android.core.network.dto.CronJobStatus.PAUSED, afterMutation.job?.status)
            cancelAndIgnoreRemainingEvents()
        }

        val pauseRequest = server.takeRequest()
        assertTrue(pauseRequest.path?.contains("/api/crons/pause") == true)
    }

    @Test
    fun `togglePauseResume resumes a paused job`() = runTest {
        val repo = loggedInRepository()
        enqueueInitialLoad("paused")
        val viewModel = TaskDetailViewModel("a", repo)
        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(com.hermex.android.core.network.dto.CronJobStatus.PAUSED, loaded.job?.status)
            cancelAndIgnoreRemainingEvents()
        }
        repeat(3) { server.takeRequest() }

        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        enqueueInitialLoad()

        viewModel.uiState.test {
            viewModel.togglePauseResume()
            awaitUntil { it.isMutating }
            awaitUntil { !it.isMutating }
            cancelAndIgnoreRemainingEvents()
        }

        val resumeRequest = server.takeRequest()
        assertTrue(resumeRequest.path?.contains("/api/crons/resume") == true)
    }

    @Test
    fun `delete only invokes onDeleted on confirmed success`() = runTest {
        val repo = loggedInRepository()
        enqueueInitialLoad()
        val viewModel = TaskDetailViewModel("a", repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        repeat(3) { server.takeRequest() }

        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        var deletedCallbackFired = false

        viewModel.uiState.test {
            viewModel.delete { deletedCallbackFired = true }
            awaitUntil { it.isMutating }
            awaitUntil { !it.isMutating }
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(deletedCallbackFired)
        val deleteRequest = server.takeRequest()
        assertTrue(deleteRequest.path?.contains("/api/crons/delete") == true)
    }

    @Test
    fun `delete failure surfaces an error and never invokes onDeleted`() = runTest {
        val repo = loggedInRepository()
        enqueueInitialLoad()
        val viewModel = TaskDetailViewModel("a", repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        repeat(3) { server.takeRequest() }

        server.enqueue(MockResponse().setBody("""{"ok":false,"error":"cannot delete a running job"}"""))
        var deletedCallbackFired = false

        viewModel.uiState.test {
            viewModel.delete { deletedCallbackFired = true }
            // isMutating defaults to false, so confirm the mutation actually started before
            // waiting for it to finish -- otherwise "!it.isMutating" matches the pre-mutation
            // state too (see SessionListViewModelTest for the same lesson learned earlier).
            awaitUntil { it.isMutating }
            val afterMutation = awaitUntil { !it.isMutating }
            assertEquals("cannot delete a running job", afterMutation.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(!deletedCallbackFired)
    }

    @Test
    fun `a network failure during a mutation surfaces an error without crashing`() = runTest {
        val repo = loggedInRepository()
        enqueueInitialLoad()
        val viewModel = TaskDetailViewModel("a", repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        repeat(3) { server.takeRequest() }

        server.enqueue(MockResponse().setResponseCode(500))

        viewModel.uiState.test {
            viewModel.runNow()
            // isMutating defaults to false, so confirm the mutation actually started before
            // waiting for it to finish -- otherwise "!it.isMutating" matches the pre-mutation
            // state too (see SessionListViewModelTest for the same lesson learned earlier).
            awaitUntil { it.isMutating }
            val afterMutation = awaitUntil { !it.isMutating }
            assertTrue(afterMutation.errorMessage != null)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
