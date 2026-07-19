package com.hermex.android.chat

import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.core.network.dto.MessageAttachment
import com.hermex.android.core.network.dto.attachmentsForDisplay
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

    @Test
    fun `a historical message whose server omitted structured attachments still produces a working thumbnail URL from the marker`() {
        // Issue #16: some session/history payloads drop ChatMessage#attachments entirely for a
        // message that clearly had one (confirmed live: the marker text still round-trips). The
        // fallback in attachmentsForDisplay() is what keeps a thumbnail (not just a bare chip)
        // possible in that case.
        val message = ChatMessage(
            role = "user",
            content = "check this out\n\n[Attached files: /home/byte/.hermes/webui/attachments/sess-1/photo.png]",
            attachments = null,
        )

        val display = message.attachmentsForDisplay()

        assertEquals(1, display.size)
        val attachment = display.first()
        assertTrue(attachment.isImageForDisplay())
        assertEquals(
            "https://hermes.example/api/file/raw?session_id=sess-1&path=photo.png",
            attachmentRawUrl("https://hermes.example", "sess-1", attachment),
        )
    }

    @Test
    fun `a marker-derived fallback attachment still exposes a filename for the chip even for a non-image file`() {
        // Even when a thumbnail can't be attempted, the chip fallback (filename + icon) must
        // still be derivable -- this is what keeps "the message had an attachment" visible when
        // a thumbnail request fails or was never possible.
        val message = ChatMessage(
            role = "user",
            content = "see attached\n\n[Attached files: /state/attachments/sess-1/report.pdf]",
            attachments = null,
        )

        val attachment = message.attachmentsForDisplay().single()

        assertEquals("report.pdf", attachment.displayFileName())
        assertFalse(attachment.isImageForDisplay())
    }
}
