package com.hermex.android.tasks

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.dto.CronJobStatus
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
class TasksViewModelTest {
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
    fun `TasksViewModel loads the job list`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"jobs":[{"job_id":"a","name":"Daily report","enabled":true},{"job_id":"b","state":"paused"}]}""",
            ),
        )

        val viewModel = TasksViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(2, loaded.jobs.size)
            assertEquals(CronJobStatus.ACTIVE, loaded.jobs[0].status)
            assertEquals(CronJobStatus.PAUSED, loaded.jobs[1].status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `TaskDetailViewModel merges job metadata, live status, and recent output`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(
            MockResponse().setBody(
                """{"jobs":[{"job_id":"a","name":"Daily report","prompt":"summarize the day"}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"job_id":"a","running":true,"elapsed":12.5}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"job_id":"a","outputs":[{"filename":"out1.txt","content":"done"}]}""",
            ),
        )

        val viewModel = TaskDetailViewModel("a", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals("Daily report", loaded.job?.name)
            assertEquals(true, loaded.isRunning)
            assertEquals(12.5, loaded.elapsedSeconds)
            assertEquals(1, loaded.outputs.size)
            assertEquals("out1.txt", loaded.outputs.first().filename)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `TaskDetailViewModel surfaces an error when the job id isn't in the list`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"jobs":[{"job_id":"other"}]}"""))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))

        val viewModel = TaskDetailViewModel("missing", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertNull(loaded.job)
            assertTrue(loaded.errorMessage != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `TaskDetailViewModel still shows job metadata even if status and output calls fail`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"jobs":[{"job_id":"a","name":"Daily report"}]}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = TaskDetailViewModel("a", repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals("Daily report", loaded.job?.name)
            assertNull(loaded.isRunning)
            assertTrue(loaded.outputs.isEmpty())
            assertNull(loaded.errorMessage) // best-effort secondary calls failing isn't a hard error
            cancelAndIgnoreRemainingEvents()
        }
    }
}
