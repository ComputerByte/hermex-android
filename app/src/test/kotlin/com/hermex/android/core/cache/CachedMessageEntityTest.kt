package com.hermex.android.core.cache

import com.hermex.android.core.network.dto.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedMessageEntityTest {
    @Test
    fun `a fully populated message round-trips through the cache entity`() {
        val original = ChatMessage(
            role = "assistant",
            content = "The answer is 42.",
            timestamp = 12345.0,
            messageId = "msg-1",
            name = "tool-name",
            toolCallId = "call-1",
            reasoning = "Thinking it over...",
        )

        val entity = original.toCachedEntity(serverId = "server-a", sessionId = "session-1", orderIndex = 3, cachedAtEpochMillis = 42L)
        val roundTripped = entity.toChatMessage()

        assertEquals(original.role, roundTripped.role)
        assertEquals(original.content, roundTripped.content)
        assertEquals(original.effectiveTimestamp, roundTripped.effectiveTimestamp)
        assertEquals(original.messageId, roundTripped.messageId)
        assertEquals(original.name, roundTripped.name)
        assertEquals(original.toolCallId, roundTripped.toolCallId)
        assertEquals(original.reasoning, roundTripped.reasoning)
        assertEquals("server-a", entity.serverId)
        assertEquals("session-1", entity.sessionId)
        assertEquals(3, entity.orderIndex)
    }

    @Test
    fun `null optional fields survive the round trip as null`() {
        val original = ChatMessage(role = "user", content = "hi")

        val entity = original.toCachedEntity("server-a", "session-1", 0, 0L)
        val roundTripped = entity.toChatMessage()

        assertNull(roundTripped.messageId)
        assertNull(roundTripped.reasoning)
        assertNull(roundTripped.name)
    }

    @Test
    fun `a message with no server messageId still caches distinctly by orderIndex`() {
        val first = ChatMessage(role = "user", content = "same content")
        val second = ChatMessage(role = "user", content = "same content")

        val entityA = first.toCachedEntity("server-a", "session-1", orderIndex = 0, cachedAtEpochMillis = 0L)
        val entityB = second.toCachedEntity("server-a", "session-1", orderIndex = 1, cachedAtEpochMillis = 0L)

        assertEquals(0, entityA.orderIndex)
        assertEquals(1, entityB.orderIndex)
    }
}
