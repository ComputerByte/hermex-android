package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDtoSerializationTest {
    @Test
    fun `ChatMessage decodes with attachments present, full object shape`() {
        val message = HermexJson.decodeFromString<ChatMessage>(
            """{
                "role": "user",
                "content": "check this out",
                "attachments": [
                    {"name": "photo.png", "path": "/state/attachments/sess-1/photo.png", "mime": "image/png", "size": 4096, "is_image": true}
                ]
            }""",
        )

        assertEquals(1, message.attachments?.size)
        val attachment = message.attachments!!.first()
        assertEquals("photo.png", attachment.name)
        assertEquals(true, attachment.isImage)
        assertFalse(attachment.wasBareReference)
    }

    @Test
    fun `ChatMessage decodes with attachments present, bare string shape from reloaded history`() {
        val message = HermexJson.decodeFromString<ChatMessage>(
            """{"role": "user", "content": "check this out", "attachments": ["photo.png"]}""",
        )

        assertEquals(1, message.attachments?.size)
        val attachment = message.attachments!!.first()
        assertEquals("photo.png", attachment.name)
        assertTrue(attachment.wasBareReference)
    }

    @Test
    fun `ChatMessage decodes without attachments field at all`() {
        val message = HermexJson.decodeFromString<ChatMessage>(
            """{"role": "assistant", "content": "hello"}""",
        )

        assertNull(message.attachments)
    }

    @Test
    fun `ChatStartRequest serializes attachments as the full object shape, snake_case`() {
        val encoded = HermexJson.encodeToString(
            ChatStartRequest(
                sessionId = "sess-1",
                message = "check this out\n\n[Attached files: /state/attachments/sess-1/photo.png]",
                attachments = listOf(
                    MessageAttachment(name = "photo.png", path = "/state/attachments/sess-1/photo.png", mime = "image/png", size = 4096, isImage = true),
                ),
            ),
        )

        assertTrue(encoded.contains("\"attachments\":[{"))
        assertTrue(encoded.contains("\"name\":\"photo.png\""))
        assertTrue(encoded.contains("\"path\":\"/state/attachments/sess-1/photo.png\""))
        assertTrue(encoded.contains("\"is_image\":true"))
    }

    @Test
    fun `ChatStartRequest omits attachments entirely when null`() {
        val encoded = HermexJson.encodeToString(
            ChatStartRequest(sessionId = "sess-1", message = "hello"),
        )

        assertFalse(encoded.contains("attachments"))
    }

    @Test
    fun `ChatStartRequest encodes an explicit empty list as an empty array, only null is omitted`() {
        val encoded = HermexJson.encodeToString(
            ChatStartRequest(sessionId = "sess-1", message = "hello", attachments = emptyList()),
        )

        assertTrue(encoded.contains("\"attachments\":[]"))
    }
}
