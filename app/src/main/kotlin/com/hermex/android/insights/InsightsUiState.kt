package com.hermex.android.insights

import com.hermex.android.core.network.dto.InsightsActivityByDay
import com.hermex.android.core.network.dto.InsightsActivityByHour
import com.hermex.android.core.network.dto.InsightsDailyToken
import com.hermex.android.core.network.dto.InsightsModelBreakdown
import com.hermex.android.core.network.dto.InsightsResponse
import com.hermex.android.core.network.dto.SessionSummary
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Mirrors iOS's `AnalyticsTimeframe` -- the segmented control maps directly to the server's
 * `days` query param (Today=1, Last 7 Days=7, Last 30 Days=30, All Time=365; there's no true
 * "all time" on the server, 365 days is what iOS treats as the ceiling). */
enum class InsightsTimeframe(val label: String, val days: Int) {
    TODAY("Today", 1),
    LAST_7_DAYS("Last 7 Days", 7),
    LAST_30_DAYS("Last 30 Days", 30),
    ALL_TIME("All Time", 365),
    ;

    /** Whether [session] falls within this timeframe, for filtering the local-fallback session
     * list -- mirrors iOS's `AnalyticsTimeframe.contains(_:now:calendar:)`. Uses whichever of
     * `lastMessageAt`/`updatedAt`/`createdAt` is present first, matching iOS's precedence; a
     * session with none of those never matches a non-[ALL_TIME] timeframe (but always counts
     * towards [ALL_TIME], timestamp or not). */
    fun contains(session: SessionSummary, now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        if (this == ALL_TIME) return true
        val epochSeconds = session.lastMessageAt ?: session.updatedAt ?: session.createdAt ?: return false
        val sessionInstant = Instant.ofEpochMilli((epochSeconds * 1000.0).toLong())
        return when (this) {
            TODAY -> sessionInstant.atZone(zoneId).toLocalDate() == now.atZone(zoneId).toLocalDate()
            LAST_7_DAYS -> !sessionInstant.isBefore(now.minus(7, ChronoUnit.DAYS)) && !sessionInstant.isAfter(now)
            LAST_30_DAYS -> !sessionInstant.isBefore(now.minus(30, ChronoUnit.DAYS)) && !sessionInstant.isAfter(now)
            ALL_TIME -> true
        }
    }
}

/** Client-computed aggregates over raw [SessionSummary] metadata -- the local-fallback substitute
 * for the server's `/api/insights` breakdown when that endpoint is unavailable. Mirrors iOS's
 * `SessionUsageAnalytics` exactly, including tolerating sessions with missing token/cost/message
 * fields (treated as 0 in every sum). */
data class SessionUsageAnalytics(val sessions: List<SessionSummary>) {
    val totalInputTokens: Int get() = sessions.sumOf { it.inputTokens ?: 0 }
    val totalOutputTokens: Int get() = sessions.sumOf { it.outputTokens ?: 0 }
    val totalTokens: Int get() = totalInputTokens + totalOutputTokens
    val totalMessages: Int get() = sessions.sumOf { it.messageCount ?: 0 }
    val estimatedCost: Double get() = sessions.sumOf { it.estimatedCost ?: 0.0 }
    val sessionCount: Int get() = sessions.size

    /** Sorted by total tokens (input + output) descending -- ties keep their original relative
     * order (Kotlin's `sortedByDescending` is stable, matching Swift's `sorted(by:)`). */
    val topSessions: List<SessionSummary>
        get() = sessions.sortedByDescending { (it.inputTokens ?: 0) + (it.outputTokens ?: 0) }
}

/** Mirrors iOS's `InsightsDataSource`: which of the three data origins is currently backing the
 * displayed numbers. */
enum class InsightsDataSource { SERVER, LOCAL_FALLBACK, LOCAL }

data class InsightsUiState(
    val isLoading: Boolean = true,
    /** The segmented control's current selection -- may differ from [loadedTimeframe] while a
     * timeframe change is still loading. */
    val timeframe: InsightsTimeframe = InsightsTimeframe.LAST_30_DAYS,
    /** The timeframe the currently-displayed data actually reflects. */
    val loadedTimeframe: InsightsTimeframe = InsightsTimeframe.LAST_30_DAYS,
    val serverInsights: InsightsResponse? = null,
    /** Raw sessions from the `/api/sessions` fallback -- empty whenever [dataSource] is [InsightsDataSource.SERVER]. */
    val sessions: List<SessionSummary> = emptyList(),
    val dataSource: InsightsDataSource = InsightsDataSource.LOCAL,
    /** Set when server insights failed but a fallback (successful or not) was attempted --
     * distinct from [errorMessage], which only applies when there's nothing to show at all. */
    val fallbackReason: String? = null,
    val errorMessage: String? = null,
) {
    val filteredSessions: List<SessionSummary>
        get() = sessions.filter { loadedTimeframe.contains(it) }

    val analytics: SessionUsageAnalytics
        get() = SessionUsageAnalytics(filteredSessions)

    /** Gates the loading/error/no-data full-screen states vs. the main list -- true once there's
     * *something* worth showing, even if a later refresh is in flight or fails. */
    val hasLoadedAnalytics: Boolean
        get() = serverInsights != null || dataSource == InsightsDataSource.LOCAL_FALLBACK

    val totalInputTokens: Int get() = serverInsights?.totalInputTokens ?: analytics.totalInputTokens
    val totalOutputTokens: Int get() = serverInsights?.totalOutputTokens ?: analytics.totalOutputTokens
    val totalTokens: Int get() = serverInsights?.totalTokens ?: analytics.totalTokens
    val totalMessages: Int get() = serverInsights?.totalMessages ?: analytics.totalMessages
    val estimatedCost: Double get() = serverInsights?.totalCost ?: analytics.estimatedCost
    val sessionCount: Int get() = serverInsights?.totalSessions ?: analytics.sessionCount

    /** Uses the live-selected [timeframe] (not [loadedTimeframe]) as the fallback, matching iOS's
     * `periodDays` exactly -- while a new timeframe's load is in flight, this already reflects
     * the new selection unless the old server response is still what's displayed. */
    val periodDays: Int get() = serverInsights?.periodDays ?: timeframe.days

    /** "All Time" reads as "Last 365 Days" once real server data confirms the actual period,
     * matching iOS -- the segmented control option itself still says "All Time". */
    val periodTitle: String
        get() = if (dataSource == InsightsDataSource.SERVER && loadedTimeframe == InsightsTimeframe.ALL_TIME) {
            "Last $periodDays Days"
        } else {
            loadedTimeframe.label
        }

    val sourceDescription: String
        get() = when (dataSource) {
            InsightsDataSource.SERVER -> "Source: server insights from the last $periodDays days."
            InsightsDataSource.LOCAL_FALLBACK -> if (!fallbackReason.isNullOrEmpty()) {
                "Source: local session metadata fallback. Server insights failed: $fallbackReason"
            } else {
                "Source: local session metadata fallback."
            }
            InsightsDataSource.LOCAL -> "Source: local session metadata."
        }

    val models: List<InsightsModelBreakdown>
        get() = serverInsights?.models.orEmpty().take(10)

    val recentDailyTokens: List<InsightsDailyToken>
        get() = serverInsights?.dailyTokens.orEmpty().takeLast(14)

    /** Computed client-side from the activity arrays, matching iOS -- the server doesn't return
     * a distinct "peak" field. */
    val peakDay: InsightsActivityByDay?
        get() = serverInsights?.activityByDay.orEmpty().maxByOrNull { it.sessions ?: 0 }

    val peakHour: InsightsActivityByHour?
        get() = serverInsights?.activityByHour.orEmpty().maxByOrNull { it.sessions ?: 0 }

    /** Only meaningful for local/fallback data -- when server insights are available, the server
     * already has its own aggregated view, so this stays empty (matching iOS: `guard dataSource
     * != .server else { return [] }`). Capped at 10 here rather than at the view layer. */
    val topSessions: List<SessionSummary>
        get() = if (dataSource == InsightsDataSource.SERVER) emptyList() else analytics.topSessions.take(10)
}
