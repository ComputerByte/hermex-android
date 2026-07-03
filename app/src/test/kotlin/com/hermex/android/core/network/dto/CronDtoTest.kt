package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CronDtoTest {
    @Test
    fun `schedule as a bare string is used directly`() {
        val job = HermexJson.decodeFromString<CronJob>("""{"schedule":"every day at 9am"}""")
        assertEquals("every day at 9am", job.schedule)
    }

    @Test
    fun `schedule as an object picks the first meaningful field in priority order`() {
        val withExpression = HermexJson.decodeFromString<CronJob>(
            """{"schedule":{"expression":"0 9 * * *","expr":"ignored","kind":"cron"}}""",
        )
        assertEquals("0 9 * * *", withExpression.schedule)

        val fallsBackToKind = HermexJson.decodeFromString<CronJob>(
            """{"schedule":{"kind":"interval"}}""",
        )
        assertEquals("interval", fallsBackToKind.schedule)
    }

    @Test
    fun `schedule absent or null does not crash`() {
        assertNull(HermexJson.decodeFromString<CronJob>("""{}""").schedule)
        assertNull(HermexJson.decodeFromString<CronJob>("""{"schedule":null}""").schedule)
    }

    @Test
    fun `next_run_at as a raw epoch number decodes directly`() {
        val job = HermexJson.decodeFromString<CronJob>("""{"next_run_at":1770000000}""")
        assertEquals(1770000000.0, job.nextRunAt)
    }

    @Test
    fun `next_run_at as a numeric string is parsed`() {
        val job = HermexJson.decodeFromString<CronJob>("""{"next_run_at":"1770000000"}""")
        assertEquals(1770000000.0, job.nextRunAt)
    }

    @Test
    fun `next_run_at as an ISO-8601 string (with and without fractional seconds) is parsed`() {
        val withoutFraction = HermexJson.decodeFromString<CronJob>(
            """{"next_run_at":"2026-02-01T09:00:00Z"}""",
        )
        assertEquals(java.time.Instant.parse("2026-02-01T09:00:00Z").epochSecond.toDouble(), withoutFraction.nextRunAt)

        val withFraction = HermexJson.decodeFromString<CronJob>(
            """{"next_run_at":"2026-02-01T09:00:00.500Z"}""",
        )
        assertEquals(
            java.time.Instant.parse("2026-02-01T09:00:00.500Z").toEpochMilli() / 1000.0,
            withFraction.nextRunAt,
        )
    }

    @Test
    fun `next_run_at as an unparseable string does not crash, yields null`() {
        val job = HermexJson.decodeFromString<CronJob>("""{"next_run_at":"not a date"}""")
        assertNull(job.nextRunAt)
    }

    @Test
    fun `enabled as numeric 0 or 1 is coerced via LossyBooleanSerializer`() {
        assertEquals(true, HermexJson.decodeFromString<CronJob>("""{"enabled":1}""").enabled)
        assertEquals(false, HermexJson.decodeFromString<CronJob>("""{"enabled":0}""").enabled)
    }

    @Test
    fun `displayName falls back from name to schedule text to a default`() {
        val named = HermexJson.decodeFromString<CronJob>("""{"name":"Daily standup"}""")
        assertEquals("Daily standup", named.displayName)

        val scheduledOnly = HermexJson.decodeFromString<CronJob>("""{"schedule":"every day at 9am"}""")
        assertEquals("every day at 9am", scheduledOnly.displayName)

        val neither = HermexJson.decodeFromString<CronJob>("""{}""")
        assertEquals("Untitled Task", neither.displayName)
    }

    @Test
    fun `status derivation -- paused beats everything, then off, then error, then active`() {
        assertEquals(CronJobStatus.PAUSED, HermexJson.decodeFromString<CronJob>("""{"state":"paused","enabled":false}""").status)
        assertEquals(CronJobStatus.OFF, HermexJson.decodeFromString<CronJob>("""{"enabled":false}""").status)
        assertEquals(CronJobStatus.ERROR, HermexJson.decodeFromString<CronJob>("""{"last_status":"error"}""").status)
        assertEquals(CronJobStatus.ACTIVE, HermexJson.decodeFromString<CronJob>("""{}""").status)
    }

    @Test
    fun `jobId falls back to the plain 'id' key when 'job_id' is absent`() {
        val response = HermexJson.decodeFromString<CronJobsResponse>(
            """{"jobs":[{"id":"abc123","name":"Daily report"}]}""",
        )
        assertEquals("abc123", response.jobs?.single()?.jobId)
    }

    @Test
    fun `jobId prefers 'id' over 'job_id' when both are present, matching iOS's decode order`() {
        val response = HermexJson.decodeFromString<CronJobsResponse>(
            """{"jobs":[{"id":"from-id","job_id":"from-job-id"}]}""",
        )
        assertEquals("from-id", response.jobs?.single()?.jobId)
    }

    @Test
    fun `jobId 'id' fallback also applies to a mutation response's job`() {
        val response = HermexJson.decodeFromString<CronMutationResponse>(
            """{"ok":true,"job":{"id":"abc123","name":"Daily report"}}""",
        )
        assertEquals("abc123", response.job?.jobId)
    }

    @Test
    fun `full jobs list response decodes tolerantly`() {
        val response = HermexJson.decodeFromString<CronJobsResponse>(
            """{"jobs":[{"job_id":"a","name":"First"},{"job_id":"b","schedule":"daily"}]}""",
        )
        assertEquals(2, response.jobs?.size)
    }
}
