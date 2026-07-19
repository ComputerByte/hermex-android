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

    // ── isImageForDisplay: MIME detection and extension fallback ──

    @Test
    fun `every recognized image extension is detected case-insensitively when MIME is absent`() {
        val extensions = listOf("avif", "bmp", "gif", "heic", "heif", "ico", "jpeg", "jpg", "png", "webp")
        for (ext in extensions) {
            assertTrue("lowercase .$ext should be an image", MessageAttachment(name = "photo.$ext").isImageForDisplay())
            assertTrue("uppercase .${ext.uppercase()} should be an image", MessageAttachment(name = "photo.${ext.uppercase()}").isImageForDisplay())
        }
    }

    @Test
    fun `an unrecognized extension with no MIME is not treated as an image`() {
        assertFalse(MessageAttachment(name = "archive.zip").isImageForDisplay())
        assertFalse(MessageAttachment(name = "no-extension-at-all").isImageForDisplay())
    }

    @Test
    fun `the isImage flag wins even when mime and extension both disagree`() {
        val attachment = MessageAttachment(name = "data.zip", mime = "application/zip", isImage = true)
        assertTrue(attachment.isImageForDisplay())
    }

    @Test
    fun `an image MIME type wins even when the extension is not in the known image list`() {
        val attachment = MessageAttachment(name = "photo.unknownext", mime = "image/svg+xml")
        assertTrue(attachment.isImageForDisplay())
    }

    @Test
    fun `a non-image MIME type does not suppress the extension fallback`() {
        // isImageForDisplay() only ever treats "isImage" / an image/* mime as a positive signal --
        // a present-but-non-image mime is not a veto, so an image-looking extension still wins.
        val attachment = MessageAttachment(name = "fake.png", mime = "application/octet-stream")
        assertTrue(attachment.isImageForDisplay())
    }

    @Test
    fun `a non-image MIME type with a non-image extension is correctly not an image`() {
        val attachment = MessageAttachment(name = "notes.txt", mime = "application/octet-stream")
        assertFalse(attachment.isImageForDisplay())
    }

    // ── displayFileName / attachmentRawUrl edge cases ──

    @Test
    fun `an attachment with neither name nor path has no display filename and no raw url`() {
        val attachment = MessageAttachment()
        assertNull(attachment.displayFileName())
        assertNull(attachmentRawUrl("https://hermes.example", "session-1", attachment))
    }

    @Test
    fun `unicode and punctuation in a filename survive basename extraction`() {
        val attachment = MessageAttachment(path = "/state/attachments/sess-1/résumé (final) #2.pdf")
        assertEquals("résumé (final) #2.pdf", attachment.displayFileName())
    }

    @Test
    fun `name takes precedence over path when both are present`() {
        val attachment = MessageAttachment(name = "preferred.png", path = "/state/attachments/sess-1/other.png")
        assertEquals("preferred.png", attachment.displayFileName())
    }
}
