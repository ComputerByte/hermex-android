package com.hermex.android.core.cache

import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.core.network.dto.SessionDetail
import com.hermex.android.core.network.dto.SessionSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository-contract tests for [OfflineCacheRepository.pruneServerCache], run against
 * [FakeOfflineCacheRepository] since Room can't execute in this project's plain-JVM test source
 * set (no Robolectric/instrumented setup). The pruning *policy* itself is covered exhaustively in
 * [CachePruningTest]; these tests cover the repository-level contract the policy is applied
 * through: list/detail reads after a prune, orphan cleanup, and per-server isolation.
 */
class OfflineCacheRepositoryPruningTest {
    private fun summary(id: String, lastMessageAt: Double? = null) = SessionSummary(sessionId = id, lastMessageAt = lastMessageAt)

    @Test
    fun `cached session list still works after a prune`() = runTest {
        val cache = FakeOfflineCacheRepository()
        cache.saveSessions("server-a", (1..5).map { summary("s$it", lastMessageAt = it.toDouble()) })

        cache.pruneServerCache("server-a", maxSessions = 2, maxAgeDays = 90, sessionIdToPreserve = null)
        val remaining = cache.cachedSessions("server-a")

        assertEquals(2, remaining.size)
        assertEquals(setOf("s4", "s5"), remaining.mapNotNull { it.sessionId }.toSet())
    }

    @Test
    fun `cached detail and messages still work for a retained session after a prune`() = runTest {
        val cache = FakeOfflineCacheRepository()
        cache.saveSessions("server-a", listOf(summary("keep", lastMessageAt = 2.0), summary("drop", lastMessageAt = 1.0)))
        cache.cacheSessionDetail(
            "server-a",
            "keep",
            SessionDetail(sessionId = "keep", messages = listOf(ChatMessage(role = "user", content = "hi"))),
        )

        cache.pruneServerCache("server-a", maxSessions = 1, maxAgeDays = 90, sessionIdToPreserve = null)
        val detail = cache.cachedSessionDetail("server-a", "keep")

        assertNotNull(detail)
        assertEquals(1, detail?.messages?.size)
        assertEquals("hi", detail?.messages?.first()?.content)
    }

    @Test
    fun `pruning a session deletes its cached messages as orphan cleanup`() = runTest {
        val cache = FakeOfflineCacheRepository()
        cache.saveSessions("server-a", listOf(summary("keep", lastMessageAt = 2.0), summary("drop", lastMessageAt = 1.0)))
        cache.cacheSessionDetail("server-a", "drop", SessionDetail(sessionId = "drop", messages = listOf(ChatMessage(role = "user", content = "bye"))))

        cache.pruneServerCache("server-a", maxSessions = 1, maxAgeDays = 90, sessionIdToPreserve = null)

        assertNull(cache.cachedSessionDetail("server-a", "drop"))
    }

    @Test
    fun `a session dropped from the list by saveSessions is swept as an orphan on the next prune`() = runTest {
        // saveSessions alone never touches cached detail (see RoomOfflineCacheRepository.saveSessions'
        // kdoc) -- this reproduces that pre-existing gap and proves pruneServerCache's orphan sweep
        // also catches sessions that went missing outside of pruning itself, not just ones it just pruned.
        val cache = FakeOfflineCacheRepository()
        cache.saveSessions("server-a", listOf(summary("s1"), summary("s2")))
        cache.cacheSessionDetail("server-a", "s1", SessionDetail(sessionId = "s1", messages = emptyList()))

        cache.saveSessions("server-a", listOf(summary("s2"))) // s1 silently disappeared server-side
        assertNotNull(cache.cachedSessionDetail("server-a", "s1")) // still lingering, as today

        cache.pruneServerCache("server-a", maxSessions = 50, maxAgeDays = 90, sessionIdToPreserve = null)

        assertNull(cache.cachedSessionDetail("server-a", "s1"))
    }

    @Test
    fun `pruning one server does not affect another server's cache`() = runTest {
        val cache = FakeOfflineCacheRepository()
        cache.saveSessions("server-a", (1..5).map { summary("a$it", lastMessageAt = it.toDouble()) })
        cache.saveSessions("server-b", (1..5).map { summary("b$it", lastMessageAt = it.toDouble()) })

        cache.pruneServerCache("server-a", maxSessions = 1, maxAgeDays = 90, sessionIdToPreserve = null)

        assertEquals(1, cache.cachedSessions("server-a").size)
        assertEquals(5, cache.cachedSessions("server-b").size) // untouched
    }

    @Test
    fun `pruning an empty server does not crash`() = runTest {
        val cache = FakeOfflineCacheRepository()

        cache.pruneServerCache("server-with-nothing-cached", maxSessions = 50, maxAgeDays = 90, sessionIdToPreserve = null)

        assertTrue(cache.cachedSessions("server-with-nothing-cached").isEmpty())
    }

    @Test
    fun `pruning preserves the active session end-to-end through the repository`() = runTest {
        val cache = FakeOfflineCacheRepository()
        cache.saveSessions("server-a", (1..5).map { summary("s$it", lastMessageAt = it.toDouble()) })
        cache.cacheSessionDetail("server-a", "s1", SessionDetail(sessionId = "s1", messages = listOf(ChatMessage(role = "user", content = "still here"))))

        // s1 is the oldest by lastMessageAt and would normally be pruned by a limit of 2.
        cache.pruneServerCache("server-a", maxSessions = 2, maxAgeDays = 90, sessionIdToPreserve = "s1")

        val remaining = cache.cachedSessions("server-a").mapNotNull { it.sessionId }.toSet()
        assertTrue("s1" in remaining)
        assertNotNull(cache.cachedSessionDetail("server-a", "s1"))
    }
}
