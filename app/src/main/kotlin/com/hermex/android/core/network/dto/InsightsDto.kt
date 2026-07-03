package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class InsightsResponse(
    val periodDays: Int? = null,
    val totalSessions: Int? = null,
    val totalMessages: Int? = null,
    val totalInputTokens: Int? = null,
    val totalOutputTokens: Int? = null,
    val totalTokens: Int? = null,
    val totalCost: Double? = null,
    val models: List<InsightsModelBreakdown>? = null,
    val dailyTokens: List<InsightsDailyToken>? = null,
    val activityByDay: List<InsightsActivityByDay>? = null,
    val activityByHour: List<InsightsActivityByHour>? = null,
)

@Serializable
data class InsightsModelBreakdown(
    val model: String? = null,
    val sessions: Int? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null,
    val sessionShare: Int? = null,
    val tokenShare: Int? = null,
    val costShare: Int? = null,
) {
    /** iOS shows whichever share metric is most meaningful: prefer the first non-zero value in
     * cost/token/session order, falling back to the first non-null one if all are zero. */
    val displayShare: Int?
        get() {
            val shares = listOfNotNull(costShare, tokenShare, sessionShare)
            return shares.firstOrNull { it > 0 } ?: shares.firstOrNull()
        }
}

@Serializable
data class InsightsDailyToken(
    val date: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val sessions: Int? = null,
    val cost: Double? = null,
) {
    val totalTokens: Int
        get() = (inputTokens ?: 0) + (outputTokens ?: 0)
}

@Serializable
data class InsightsActivityByDay(
    val day: String? = null,
    val sessions: Int? = null,
)

@Serializable
data class InsightsActivityByHour(
    val hour: Int? = null,
    val sessions: Int? = null,
)
