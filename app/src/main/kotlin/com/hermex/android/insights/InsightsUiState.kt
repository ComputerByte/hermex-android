package com.hermex.android.insights

import com.hermex.android.core.network.dto.InsightsActivityByDay
import com.hermex.android.core.network.dto.InsightsActivityByHour
import com.hermex.android.core.network.dto.InsightsDailyToken
import com.hermex.android.core.network.dto.InsightsModelBreakdown
import com.hermex.android.core.network.dto.InsightsResponse

/** Mirrors iOS's `AnalyticsTimeframe` -- the segmented control maps directly to the server's
 * `days` query param (Today=1, Last 7 Days=7, Last 30 Days=30, All Time=365; there's no true
 * "all time" on the server, 365 days is what iOS treats as the ceiling). */
enum class InsightsTimeframe(val label: String, val days: Int) {
    TODAY("Today", 1),
    LAST_7_DAYS("Last 7 Days", 7),
    LAST_30_DAYS("Last 30 Days", 30),
    ALL_TIME("All Time", 365),
}

data class InsightsUiState(
    val isLoading: Boolean = true,
    val timeframe: InsightsTimeframe = InsightsTimeframe.LAST_30_DAYS,
    val insights: InsightsResponse? = null,
    val errorMessage: String? = null,
) {
    val models: List<InsightsModelBreakdown>
        get() = insights?.models.orEmpty().take(10)

    val recentDailyTokens: List<InsightsDailyToken>
        get() = insights?.dailyTokens.orEmpty().takeLast(14)

    /** Computed client-side from the activity arrays, matching iOS -- the server doesn't return
     * a distinct "peak" field. */
    val peakDay: InsightsActivityByDay?
        get() = insights?.activityByDay.orEmpty().maxByOrNull { it.sessions ?: 0 }

    val peakHour: InsightsActivityByHour?
        get() = insights?.activityByHour.orEmpty().maxByOrNull { it.sessions ?: 0 }
}
