package com.hermex.android.core.cache

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SERVER = "server-a"
private val NOW = TimeUnit.DAYS.toMillis(1_000) // an arbitrary, comfortably-far-from-zero "now"

class CachePruningTest {
    private fun session(
        id: String,
        lastMessageAt: Double? = null,
        cachedAtEpochMillis: Long = NOW,
    ) = CachedSessionEntity(
        serverId = SERVER,
        sessionId = id,
        title = null,
        workspace = null,
        model = null,
        modelProvider = null,
        messageCount = null,
        createdAt = null,
        updatedAt = null,
        lastMessageAt = lastMessageAt,
        pinned = null,
        archived = null,
        projectId = null,
        profile = null,
        inputTokens = null,
        outputTokens = null,
        estimatedCost = null,
        cachedAtEpochMillis = cachedAtEpochMillis,
    )

    @Test
    fun `pruning no-ops when under the session limit`() {
        val sessions = listOf(session("s1"), session("s2"), session("s3"))

        val pruned = sessionIdsToPrune(sessions, maxSessions = 50, maxAgeDays = 90, nowEpochMillis = NOW, sessionIdToPreserve = null)

        assertTrue(pruned.isEmpty())
    }

    @Test
    fun `pruning handles an empty server without crashing`() {
        val pruned = sessionIdsToPrune(emptyList(), maxSessions = 50, maxAgeDays = 90, nowEpochMillis = NOW, sessionIdToPreserve = null)

        assertTrue(pruned.isEmpty())
    }

    @Test
    fun `pruning keeps only the latest N sessions by lastMessageAt`() {
        val sessions = (1..5).map { session(id = "s$it", lastMessageAt = it.toDouble()) }

        val pruned = sessionIdsToPrune(sessions, maxSessions = 2, maxAgeDays = 90, nowEpochMillis = NOW, sessionIdToPreserve = null)

        // s5 and s4 have the highest lastMessageAt -- everything else is pruned.
        assertEquals(setOf("s1", "s2", "s3"), pruned)
    }

    @Test
    fun `a session with no lastMessageAt falls back to cachedAtEpochMillis for ordering`() {
        val old = session(id = "old", lastMessageAt = null, cachedAtEpochMillis = NOW - TimeUnit.DAYS.toMillis(10))
        val recent = session(id = "recent", lastMessageAt = null, cachedAtEpochMillis = NOW)

        val pruned = sessionIdsToPrune(listOf(old, recent), maxSessions = 1, maxAgeDays = 90, nowEpochMillis = NOW, sessionIdToPreserve = null)

        assertEquals(setOf("old"), pruned)
    }

    @Test
    fun `pruning always preserves the explicitly provided active session, even if it would otherwise be pruned`() {
        // "active" is the oldest by lastMessageAt, and the limit is tight enough to normally drop it.
        val active = session(id = "active", lastMessageAt = 1.0)
        val others = (2..6).map { session(id = "s$it", lastMessageAt = it.toDouble()) }

        val pruned = sessionIdsToPrune(
            listOf(active) + others,
            maxSessions = 2,
            maxAgeDays = 90,
            nowEpochMillis = NOW,
            sessionIdToPreserve = "active",
        )

        assertTrue("active" !in pruned)
        // The 2 most recent *other* sessions survive alongside the preserved one.
        assertEquals(setOf("s2", "s3", "s4"), pruned)
    }

    @Test
    fun `pruning respects max age even when under the session count limit`() {
        val fresh = session(id = "fresh", cachedAtEpochMillis = NOW)
        val stale = session(id = "stale", cachedAtEpochMillis = NOW - TimeUnit.DAYS.toMillis(91))

        val pruned = sessionIdsToPrune(listOf(fresh, stale), maxSessions = 50, maxAgeDays = 90, nowEpochMillis = NOW, sessionIdToPreserve = null)

        assertEquals(setOf("stale"), pruned)
    }

    @Test
    fun `a session exactly at the age cutoff is kept, not pruned`() {
        val atCutoff = session(id = "at-cutoff", cachedAtEpochMillis = NOW - TimeUnit.DAYS.toMillis(90))

        val pruned = sessionIdsToPrune(listOf(atCutoff), maxSessions = 50, maxAgeDays = 90, nowEpochMillis = NOW, sessionIdToPreserve = null)

        assertTrue(pruned.isEmpty())
    }

    @Test
    fun `max age is only evaluated among sessions that already survived the count cap`() {
        // 3 fresh sessions push the count limit down to 1; the 4th (stale) is dropped by count,
        // and the fresh survivor is still within the age window -- max age shouldn't independently
        // resurrect the stale one that already lost on count.
        val fresh1 = session(id = "fresh1", lastMessageAt = 3.0, cachedAtEpochMillis = NOW)
        val fresh2 = session(id = "fresh2", lastMessageAt = 2.0, cachedAtEpochMillis = NOW)
        val stale = session(id = "stale", lastMessageAt = 1.0, cachedAtEpochMillis = NOW - TimeUnit.DAYS.toMillis(200))

        val pruned = sessionIdsToPrune(
            listOf(fresh1, fresh2, stale),
            maxSessions = 1,
            maxAgeDays = 90,
            nowEpochMillis = NOW,
            sessionIdToPreserve = null,
        )

        assertEquals(setOf("fresh2", "stale"), pruned)
    }
}
