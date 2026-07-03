package com.hermex.android.core.cache

import com.hermex.android.core.network.dto.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedSessionEntityTest {
    @Test
    fun `a fully populated SessionSummary round-trips through the cache entity`() {
        val original = SessionSummary(
            sessionId = "s1",
            title = "Fix login bug",
            workspace = "/home/byte/workspace",
            model = "claude-sonnet-4-6",
            modelProvider = "anthropic",
            messageCount = 12,
            createdAt = 1000.0,
            updatedAt = 2000.0,
            lastMessageAt = 3000.0,
            pinned = true,
            archived = false,
            projectId = "proj-1",
            profile = "default",
            inputTokens = 500,
            outputTokens = 250,
            estimatedCost = 0.0123,
        )

        val entity = original.toCachedEntity(serverId = "server-a", cachedAtEpochMillis = 42L)
        val roundTripped = entity?.toSessionSummary()

        assertEquals(original, roundTripped)
        assertEquals("server-a", entity?.serverId)
        assertEquals(42L, entity?.cachedAtEpochMillis)
    }

    @Test
    fun `null optional fields survive the round trip as null, not defaults`() {
        val original = SessionSummary(sessionId = "s2")

        val entity = original.toCachedEntity(serverId = "server-a", cachedAtEpochMillis = 0L)
        val roundTripped = entity?.toSessionSummary()

        assertEquals(original, roundTripped)
        assertNull(roundTripped?.title)
        assertNull(roundTripped?.lastMessageAt)
    }

    @Test
    fun `a session with no sessionId cannot be cached`() {
        val orphan = SessionSummary(sessionId = null, title = "No id")

        val entity = orphan.toCachedEntity(serverId = "server-a", cachedAtEpochMillis = 0L)

        assertNull(entity)
    }

    @Test
    fun `entities for the same session under different servers stay distinct`() {
        val session = SessionSummary(sessionId = "shared-id", title = "Same id, different servers")

        val entityA = session.toCachedEntity(serverId = "server-a", cachedAtEpochMillis = 0L)
        val entityB = session.toCachedEntity(serverId = "server-b", cachedAtEpochMillis = 0L)

        assertEquals("server-a", entityA?.serverId)
        assertEquals("server-b", entityB?.serverId)
        assertEquals(entityA?.sessionId, entityB?.sessionId) // same session id, different scope
    }
}
