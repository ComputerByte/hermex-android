package com.hermex.android.insights

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
class InsightsViewModelTest {
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
    fun `loads with the default Last 30 Days timeframe`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"total_sessions":329}"""))

        val viewModel = InsightsViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(InsightsTimeframe.LAST_30_DAYS, loaded.timeframe)
            assertEquals(329, loaded.insights?.totalSessions)
            assertNull(loaded.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }

        val request = server.takeRequest()
        assertTrue(request.path?.contains("days=30") == true)
    }

    @Test
    fun `selectTimeframe re-fetches from the server with the new days parameter`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"total_sessions":329}"""))
        val viewModel = InsightsViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        server.takeRequest() // initial load (days=30)

        server.enqueue(MockResponse().setBody("""{"total_sessions":12}"""))

        viewModel.uiState.test {
            viewModel.selectTimeframe(InsightsTimeframe.TODAY)
            awaitUntil { it.isLoading }
            val afterLoad = awaitUntil { !it.isLoading }
            assertEquals(InsightsTimeframe.TODAY, afterLoad.timeframe)
            assertEquals(12, afterLoad.insights?.totalSessions)
            cancelAndIgnoreRemainingEvents()
        }

        val request = server.takeRequest()
        assertTrue(request.path?.contains("days=1") == true)
    }

    @Test
    fun `selecting the already-active timeframe is a no-op`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"total_sessions":329}"""))
        val viewModel = InsightsViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        server.takeRequest() // initial load

        viewModel.selectTimeframe(InsightsTimeframe.LAST_30_DAYS)

        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `surfaces an error without crashing on a load failure`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = InsightsViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.errorMessage != null)
            assertNull(loaded.insights)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `models list is capped at 10 and recentDailyTokens at the last 14 entries`() {
        val manyModels = (1..15).map { com.hermex.android.core.network.dto.InsightsModelBreakdown(model = "m$it") }
        val manyDays = (1..20).map { com.hermex.android.core.network.dto.InsightsDailyToken(date = "day$it") }
        val state = InsightsUiState(
            insights = com.hermex.android.core.network.dto.InsightsResponse(models = manyModels, dailyTokens = manyDays),
        )
        assertEquals(10, state.models.size)
        assertEquals(14, state.recentDailyTokens.size)
        assertEquals("day20", state.recentDailyTokens.last().date) // last 14, preserving trailing/most-recent entries
    }

    @Test
    fun `peakDay and peakHour pick the entry with the most sessions`() {
        val state = InsightsUiState(
            insights = com.hermex.android.core.network.dto.InsightsResponse(
                activityByDay = listOf(
                    com.hermex.android.core.network.dto.InsightsActivityByDay(day = "Mon", sessions = 170),
                    com.hermex.android.core.network.dto.InsightsActivityByDay(day = "Tue", sessions = 90),
                ),
                activityByHour = listOf(
                    com.hermex.android.core.network.dto.InsightsActivityByHour(hour = 4, sessions = 177),
                    com.hermex.android.core.network.dto.InsightsActivityByHour(hour = 9, sessions = 50),
                ),
            ),
        )
        assertEquals("Mon", state.peakDay?.day)
        assertEquals(4, state.peakHour?.hour)
    }

    @Test
    fun `peakDay and peakHour are null when there's no activity data`() {
        val state = InsightsUiState(insights = com.hermex.android.core.network.dto.InsightsResponse())
        assertNull(state.peakDay)
        assertNull(state.peakHour)
    }
}
