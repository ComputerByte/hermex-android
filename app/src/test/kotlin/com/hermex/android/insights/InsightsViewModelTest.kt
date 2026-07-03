package com.hermex.android.insights

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.FakeCookieStore
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.dto.InsightsActivityByDay
import com.hermex.android.core.network.dto.InsightsActivityByHour
import com.hermex.android.core.network.dto.InsightsDailyToken
import com.hermex.android.core.network.dto.InsightsModelBreakdown
import com.hermex.android.core.network.dto.InsightsResponse
import com.hermex.android.core.network.dto.SessionSummary
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
import java.time.Instant
import java.time.ZoneId

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

    // -- load() / fallback behavior, mirroring InsightsViewModelTests.swift --

    @Test
    fun `loads with the default Last 30 Days timeframe`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"total_sessions":329}"""))

        val viewModel = InsightsViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(InsightsTimeframe.LAST_30_DAYS, loaded.timeframe)
            assertEquals(InsightsDataSource.SERVER, loaded.dataSource)
            assertEquals(329, loaded.serverInsights?.totalSessions)
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
            assertEquals(12, afterLoad.serverInsights?.totalSessions)
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
    fun `load falls back to local session analytics when server insights fails`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500)) // GET /api/insights fails
        val recentEpochSeconds = System.currentTimeMillis() / 1000.0
        server.enqueue(
            MockResponse().setBody(
                """{"sessions":[
                    {"session_id":"a","title":"Recent","created_at":$recentEpochSeconds,"message_count":4,"input_tokens":10,"output_tokens":20,"estimated_cost":0.12},
                    {"session_id":"b","title":"Older","created_at":$recentEpochSeconds,"message_count":2,"input_tokens":5,"output_tokens":7,"estimated_cost":0.03}
                ]}""",
            ),
        ) // GET /api/sessions succeeds

        val viewModel = InsightsViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(InsightsDataSource.LOCAL_FALLBACK, loaded.dataSource)
            assertEquals(2, loaded.sessionCount)
            assertEquals(6, loaded.totalMessages)
            assertEquals(15, loaded.totalInputTokens)
            assertEquals(27, loaded.totalOutputTokens)
            assertEquals(42, loaded.totalTokens)
            assertEquals(0.15, loaded.estimatedCost, 0.0001)
            assertNull(loaded.errorMessage)
            assertTrue(loaded.fallbackReason != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load surfaces an error when both server insights and the sessions fallback fail`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setResponseCode(500)) // GET /api/insights fails
        server.enqueue(MockResponse().setResponseCode(500)) // GET /api/sessions also fails

        val viewModel = InsightsViewModel(repo)

        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(loaded.errorMessage != null)
            assertEquals(InsightsDataSource.LOCAL, loaded.dataSource)
            assertTrue(!loaded.hasLoadedAnalytics)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refresh failure after analytics were already loaded keeps the old data and sets fallbackReason, not errorMessage`() = runTest {
        val repo = loggedInRepository()
        val recentEpochSeconds = System.currentTimeMillis() / 1000.0
        server.enqueue(MockResponse().setResponseCode(500)) // GET /api/insights fails
        server.enqueue(
            MockResponse().setBody(
                """{"sessions":[{"session_id":"a","title":"Recent","created_at":$recentEpochSeconds,"input_tokens":10,"output_tokens":20}]}""",
            ),
        ) // GET /api/sessions succeeds (fallback)
        val viewModel = InsightsViewModel(repo)
        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(InsightsDataSource.LOCAL_FALLBACK, loaded.dataSource)
            cancelAndIgnoreRemainingEvents()
        }

        // A subsequent refresh where BOTH calls now fail.
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        viewModel.uiState.test {
            viewModel.load()
            awaitUntil { it.isLoading }
            val afterRefresh = awaitUntil { !it.isLoading }
            assertNull(afterRefresh.errorMessage)
            assertTrue(afterRefresh.fallbackReason != null)
            // previously-loaded session data is untouched
            assertEquals(1, afterRefresh.sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching timeframe keeps previously-loaded analytics visible until the new load lands`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"period_days":30,"total_sessions":5,"total_tokens":350}"""))
        val viewModel = InsightsViewModel(repo)
        viewModel.uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(350, loaded.totalTokens)
            assertEquals("Last 30 Days", loaded.periodTitle)
            cancelAndIgnoreRemainingEvents()
        }

        // Don't enqueue the next response yet -- the new load should stay "isLoading" with the
        // old data still visible until a response actually arrives.
        viewModel.uiState.test {
            viewModel.selectTimeframe(InsightsTimeframe.TODAY)
            val whileLoading = awaitUntil { it.isLoading }
            assertEquals(350, whileLoading.totalTokens)

            server.enqueue(MockResponse().setBody("""{"period_days":1,"total_sessions":2,"total_tokens":125}"""))
            val afterLoad = awaitUntil { !it.isLoading }
            assertEquals(125, afterLoad.totalTokens)
            assertEquals("Today", afterLoad.periodTitle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `periodTitle reads All Time as Last 365 Days once server data confirms the period`() = runTest {
        val repo = loggedInRepository()
        server.enqueue(MockResponse().setBody("""{"period_days":365,"total_sessions":40}"""))
        val viewModel = InsightsViewModel(repo)
        viewModel.uiState.test { awaitUntil { !it.isLoading }; cancelAndIgnoreRemainingEvents() }
        server.takeRequest()

        server.enqueue(MockResponse().setBody("""{"period_days":365,"total_sessions":40}"""))

        viewModel.uiState.test {
            viewModel.selectTimeframe(InsightsTimeframe.ALL_TIME)
            awaitUntil { it.isLoading }
            val loaded = awaitUntil { !it.isLoading }
            assertEquals("Last 365 Days", loaded.periodTitle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `models list is capped at 10 and recentDailyTokens at the last 14 entries`() {
        val manyModels = (1..15).map { InsightsModelBreakdown(model = "m$it") }
        val manyDays = (1..20).map { InsightsDailyToken(date = "day$it") }
        val state = InsightsUiState(
            serverInsights = InsightsResponse(models = manyModels, dailyTokens = manyDays),
            dataSource = InsightsDataSource.SERVER,
        )
        assertEquals(10, state.models.size)
        assertEquals(14, state.recentDailyTokens.size)
        assertEquals("day20", state.recentDailyTokens.last().date) // last 14, preserving trailing/most-recent entries
    }

    @Test
    fun `peakDay and peakHour pick the entry with the most sessions`() {
        val state = InsightsUiState(
            serverInsights = InsightsResponse(
                activityByDay = listOf(
                    InsightsActivityByDay(day = "Mon", sessions = 170),
                    InsightsActivityByDay(day = "Tue", sessions = 90),
                ),
                activityByHour = listOf(
                    InsightsActivityByHour(hour = 4, sessions = 177),
                    InsightsActivityByHour(hour = 9, sessions = 50),
                ),
            ),
            dataSource = InsightsDataSource.SERVER,
        )
        assertEquals("Mon", state.peakDay?.day)
        assertEquals(4, state.peakHour?.hour)
    }

    @Test
    fun `peakDay and peakHour are null when there's no activity data`() {
        val state = InsightsUiState(serverInsights = InsightsResponse(), dataSource = InsightsDataSource.SERVER)
        assertNull(state.peakDay)
        assertNull(state.peakHour)
    }

    // -- SessionUsageAnalytics, mirroring testAggregateMathToleratesMissingUsageFields /
    // testTopSessionsSortsByTotalTokensWithinFilteredData --

    @Test
    fun `SessionUsageAnalytics aggregate math tolerates missing usage fields`() {
        val sessions = listOf(
            SessionSummary(title = "Complete", inputTokens = 10, outputTokens = 20, estimatedCost = 0.12),
            SessionSummary(title = "Input only", inputTokens = 7, outputTokens = null, estimatedCost = null),
            SessionSummary(title = "Cost only", inputTokens = null, outputTokens = null, estimatedCost = 0.03),
        )
        val analytics = SessionUsageAnalytics(sessions)

        assertEquals(3, analytics.sessionCount)
        assertEquals(17, analytics.totalInputTokens)
        assertEquals(20, analytics.totalOutputTokens)
        assertEquals(37, analytics.totalTokens)
        assertEquals(0.15, analytics.estimatedCost, 0.0001)
    }

    @Test
    fun `SessionUsageAnalytics topSessions sorts by total tokens descending`() {
        val sessions = listOf(
            SessionSummary(title = "Low", inputTokens = 5, outputTokens = 5),
            SessionSummary(title = "High", inputTokens = 20, outputTokens = 1),
            SessionSummary(title = "Medium", inputTokens = null, outputTokens = 12),
        )
        val analytics = SessionUsageAnalytics(sessions)

        assertEquals(listOf("High", "Medium", "Low"), analytics.topSessions.map { it.title })
    }

    @Test
    fun `topSessions is empty when data source is SERVER, even with local sessions present`() {
        val state = InsightsUiState(
            sessions = listOf(SessionSummary(title = "x", inputTokens = 5)),
            dataSource = InsightsDataSource.SERVER,
        )
        assertTrue(state.topSessions.isEmpty())
    }

    @Test
    fun `topSessions is capped at 10 for local or fallback data`() {
        val sessions = (1..15).map { SessionSummary(title = "s$it", inputTokens = it) }
        val state = InsightsUiState(
            sessions = sessions,
            loadedTimeframe = InsightsTimeframe.ALL_TIME,
            dataSource = InsightsDataSource.LOCAL_FALLBACK,
        )
        assertEquals(10, state.topSessions.size)
    }

    // -- InsightsTimeframe.contains, mirroring testTimeframeFilteringUsesMostRecentSessionTimestamp --

    @Test
    fun `timeframe filtering uses the most recent of lastMessageAt, updatedAt, or createdAt`() {
        val zone = ZoneId.of("UTC")
        val now = Instant.ofEpochSecond(1_800_000_000L)
        val nowSeconds = now.epochSecond.toDouble()
        val day = 24.0 * 60 * 60

        val today = SessionSummary(title = "Today", createdAt = nowSeconds - 60)
        val updatedRecently = SessionSummary(
            title = "Updated recently",
            createdAt = nowSeconds - 100 * day,
            updatedAt = nowSeconds - 3 * day,
        )
        val lastMessageRecently = SessionSummary(
            title = "Last message recently",
            createdAt = nowSeconds - 100 * day,
            updatedAt = nowSeconds - 40 * day,
            lastMessageAt = nowSeconds - 20 * day,
        )
        val old = SessionSummary(title = "Old", createdAt = nowSeconds - 45 * day)
        val noTimestamp = SessionSummary(title = "No timestamp", createdAt = null)
        val sessions = listOf(today, updatedRecently, lastMessageRecently, old, noTimestamp)

        fun namesMatching(timeframe: InsightsTimeframe) =
            sessions.filter { timeframe.contains(it, now, zone) }.map { it.title }

        assertEquals(listOf("Today"), namesMatching(InsightsTimeframe.TODAY))
        assertEquals(listOf("Today", "Updated recently"), namesMatching(InsightsTimeframe.LAST_7_DAYS))
        assertEquals(
            listOf("Today", "Updated recently", "Last message recently"),
            namesMatching(InsightsTimeframe.LAST_30_DAYS),
        )
        assertEquals(5, namesMatching(InsightsTimeframe.ALL_TIME).size)
    }

    @Test
    fun `timeframes map to the server insight days parameter`() {
        assertEquals(1, InsightsTimeframe.TODAY.days)
        assertEquals(7, InsightsTimeframe.LAST_7_DAYS.days)
        assertEquals(30, InsightsTimeframe.LAST_30_DAYS.days)
        assertEquals(365, InsightsTimeframe.ALL_TIME.days)
    }
}
