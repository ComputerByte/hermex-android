package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightsDtoTest {
    @Test
    fun `full insights response decodes tolerantly`() {
        val response = HermexJson.decodeFromString<InsightsResponse>(
            """{
                "period_days":30,
                "total_sessions":329,
                "total_messages":9561,
                "total_input_tokens":34693737,
                "total_output_tokens":754559,
                "total_tokens":35448296,
                "total_cost":2.3305,
                "models":[{"model":"claude-sonnet-4-6","sessions":7,"total_tokens":1411334,"cost":1.9491,"cost_share":84}],
                "daily_tokens":[{"date":"2026-07-03","input_tokens":1000,"output_tokens":200,"sessions":12,"cost":1.9491}],
                "activity_by_day":[{"day":"Mon","sessions":170},{"day":"Tue","sessions":90}],
                "activity_by_hour":[{"hour":4,"sessions":177},{"hour":9,"sessions":50}]
            }""",
        )
        assertEquals(30, response.periodDays)
        assertEquals(329, response.totalSessions)
        assertEquals(2.3305, response.totalCost)
        assertEquals(1, response.models?.size)
        assertEquals("claude-sonnet-4-6", response.models?.first()?.model)
        assertEquals(1, response.dailyTokens?.size)
        assertEquals(2, response.activityByDay?.size)
        assertEquals(2, response.activityByHour?.size)
    }

    @Test
    fun `missing fields decode as null without crashing`() {
        val response = HermexJson.decodeFromString<InsightsResponse>("""{}""")
        assertNull(response.totalSessions)
        assertNull(response.models)
        assertNull(response.dailyTokens)
    }

    @Test
    fun `displayShare prefers the first non-zero of cost, token, session share`() {
        assertEquals(84, InsightsModelBreakdown(costShare = 84, tokenShare = 41, sessionShare = 2).displayShare)
        assertEquals(41, InsightsModelBreakdown(costShare = 0, tokenShare = 41, sessionShare = 2).displayShare)
        assertEquals(2, InsightsModelBreakdown(costShare = 0, tokenShare = 0, sessionShare = 2).displayShare)
    }

    @Test
    fun `displayShare falls back to the first non-null share when all are zero`() {
        assertEquals(0, InsightsModelBreakdown(costShare = 0, tokenShare = 0, sessionShare = 0).displayShare)
    }

    @Test
    fun `displayShare is null when no share fields are present`() {
        assertNull(InsightsModelBreakdown().displayShare)
    }

    @Test
    fun `daily token totalTokens sums input and output, defaulting missing values to zero`() {
        assertEquals(1200, InsightsDailyToken(inputTokens = 1000, outputTokens = 200).totalTokens)
        assertEquals(1000, InsightsDailyToken(inputTokens = 1000, outputTokens = null).totalTokens)
        assertEquals(0, InsightsDailyToken().totalTokens)
    }
}
