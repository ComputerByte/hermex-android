package com.hermex.android.chat

import com.hermex.android.core.network.dto.MessageAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentDisplayTest {
    @Test
    fun `raw URL uses authenticated endpoint with session and basename`() {
        val attachment = MessageAttachment(
            name = "photo one.png",
            path = "/state/attachments/session-1/photo one.png",
            mime = "image/png",
        )

        assertEquals(
            "https://hermes.example/api/file/raw?session_id=session-1&path=photo%20one.png",
            attachmentRawUrl("https://hermes.example/", "session-1", attachment),
        )
    }

    @Test
    fun `historical bare filename is recognized as an image`() {
        val attachment = MessageAttachment(name = "SCREENSHOT.JPEG", wasBareReference = true)

        assertEquals("SCREENSHOT.JPEG", attachment.displayFileName())
        assertTrue(attachment.isImageForDisplay())
        assertEquals(
            "https://hermes.example/api/file/raw?session_id=old%20chat&path=SCREENSHOT.JPEG",
            attachmentRawUrl("https://hermes.example", "old chat", attachment),
        )
    }

    @Test
    fun `server filesystem paths are reduced to a safe filename`() {
        val attachment = MessageAttachment(path = "C:\\server\\uploads\\image.webp")

        assertEquals("image.webp", attachment.displayFileName())
        assertTrue(attachment.isImageForDisplay())
    }

    @Test
    fun `non-images and incomplete URL inputs are rejected`() {
        val attachment = MessageAttachment(name = "notes.pdf")

        assertFalse(attachment.isImageForDisplay())
        assertNull(attachmentRawUrl(null, "session-1", attachment))
        assertNull(attachmentRawUrl("https://hermes.example", null, attachment))
    }
}
