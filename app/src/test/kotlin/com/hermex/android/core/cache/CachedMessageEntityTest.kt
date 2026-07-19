package com.hermex.android.core.cache

import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.core.network.dto.MessageAttachment
import com.hermex.android.core.network.dto.attachmentsForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            attachments = listOf(
                MessageAttachment(
                    name = "photo.png",
                    path = "/state/attachments/session-1/photo.png",
                    mime = "image/png",
                    size = 1234,
                    isImage = true,
                ),
                MessageAttachment(name = "historical.jpg", wasBareReference = true),
            ),
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
        assertEquals(original.attachments, roundTripped.attachments)
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
    fun `a message with null attachments but a surviving marker still recovers a display attachment after a cache round trip`() {
        // Reproduces issue #16: a server whose history payload omits the structured `attachments`
        // array for a message still echoes the `[Attached files: ...]` marker in its content.
        val original = ChatMessage(
            role = "user",
            content = "check this out\n\n[Attached files: /state/attachments/session-1/photo.png]",
            attachments = null,
        )

        val entity = original.toCachedEntity("server-a", "session-1", 0, 0L)
        val roundTripped = entity.toChatMessage()

        assertNull(roundTripped.attachments)
        val display = roundTripped.attachmentsForDisplay()
        assertEquals(1, display.size)
        assertEquals("/state/attachments/session-1/photo.png", display.first().path)
        assertTrue(display.first().wasBareReference)
    }

    @Test
    fun `a corrupted attachmentsJson column decodes to null instead of throwing`() {
        val entity = CachedMessageEntity(
            serverId = "server-a",
            sessionId = "session-1",
            orderIndex = 0,
            role = "user",
            content = "hi\n\n[Attached files: photo.png]",
            reasoning = null,
            timestamp = null,
            messageId = null,
            name = null,
            toolCallId = null,
            attachmentsJson = "{not valid json[",
            cachedAtEpochMillis = 0L,
        )

        val roundTripped = entity.toChatMessage()

        assertNull(roundTripped.attachments)
        assertEquals(1, roundTripped.attachmentsForDisplay().size)
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
