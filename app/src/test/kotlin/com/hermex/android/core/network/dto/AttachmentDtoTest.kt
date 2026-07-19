package com.hermex.android.core.network.dto

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Videocam
import com.hermex.android.chat.displayFileName
import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentDtoTest {
    @Test
    fun `UploadResponse decodes a successful upload`() {
        val response = HermexJson.decodeFromString<UploadResponse>(
            """{"filename": "photo.png", "path": "/state/attachments/sess-1/photo.png", "size": 4096, "mime": "image/png", "is_image": true}""",
        )

        assertEquals("photo.png", response.filename)
        assertEquals("/state/attachments/sess-1/photo.png", response.path)
        assertEquals(4096L, response.size)
        assertEquals("image/png", response.mime)
        assertEquals(true, response.isImage)
        assertNull(response.error)
    }

    @Test
    fun `UploadResponse decodes an error response`() {
        val response = HermexJson.decodeFromString<UploadResponse>(
            """{"error": "File too large (max 20MB)"}""",
        )

        assertEquals("File too large (max 20MB)", response.error)
        assertNull(response.filename)
        assertNull(response.path)
        assertNull(response.size)
        assertNull(response.mime)
        assertNull(response.isImage)
    }

    @Test
    fun `UploadResponse tolerates missing and unknown fields`() {
        val sparse = HermexJson.decodeFromString<UploadResponse>("""{}""")
        assertNull(sparse.filename)
        assertNull(sparse.error)

        val withUnknown = HermexJson.decodeFromString<UploadResponse>(
            """{"filename": "a.txt", "totally_new_field": 42}""",
        )
        assertEquals("a.txt", withUnknown.filename)
    }

    @Test
    fun `MessageAttachment decodes the full object shape`() {
        val attachment = HermexJson.decodeFromString<MessageAttachment>(
            """{"name": "report.pdf", "path": "/state/attachments/sess-1/report.pdf", "mime": "application/pdf", "size": 2048, "is_image": false}""",
        )

        assertEquals("report.pdf", attachment.name)
        assertEquals("/state/attachments/sess-1/report.pdf", attachment.path)
        assertEquals("application/pdf", attachment.mime)
        assertEquals(2048L, attachment.size)
        assertEquals(false, attachment.isImage)
        assertFalse(attachment.wasBareReference)
    }

    @Test
    fun `MessageAttachment tolerates a filename key as an alternate to name`() {
        val attachment = HermexJson.decodeFromString<MessageAttachment>(
            """{"filename": "legacy.txt", "path": "legacy.txt"}""",
        )

        assertEquals("legacy.txt", attachment.name)
        assertFalse(attachment.wasBareReference)
    }

    @Test
    fun `MessageAttachment decodes a bare string as a name-only legacy reference`() {
        val attachment = HermexJson.decodeFromString<MessageAttachment>(""""old-upload.png"""")

        assertEquals("old-upload.png", attachment.name)
        assertNull(attachment.path)
        assertNull(attachment.mime)
        assertNull(attachment.size)
        assertNull(attachment.isImage)
        assertTrue(attachment.wasBareReference)
    }

    @Test
    fun `MessageAttachment ignores unknown fields in the object shape`() {
        val attachment = HermexJson.decodeFromString<MessageAttachment>(
            """{"name": "a.png", "totally_new_field": {"nested": true}}""",
        )

        assertEquals("a.png", attachment.name)
    }

    @Test
    fun `MessageAttachment always encodes as the full object shape`() {
        val encoded = HermexJson.encodeToString(
            MessageAttachment(name = "a.png", path = "/x/a.png", mime = "image/png", size = 10, isImage = true),
        )

        assertTrue(encoded.contains("\"name\":\"a.png\""))
        assertTrue(encoded.contains("\"path\":\"/x/a.png\""))
        assertTrue(encoded.contains("\"mime\":\"image/png\""))
        assertTrue(encoded.contains("\"size\":10"))
        assertTrue(encoded.contains("\"is_image\":true"))
    }

    @Test
    fun `buildAttachedFilesMarker matches the confirmed server-webui-iOS format`() {
        assertEquals(
            "\n\n[Attached files: path1, path2]",
            buildAttachedFilesMarker(listOf("path1", "path2")),
        )
    }

    @Test
    fun `buildAttachedFilesMarker returns empty string for no attachments`() {
        assertEquals("", buildAttachedFilesMarker(emptyList()))
    }

    @Test
    fun `parseAttachedFilesMarker recovers a single full server path`() {
        val content = "check this out\n\n[Attached files: /state/attachments/sess-1/photo.png]"
        assertEquals(listOf("/state/attachments/sess-1/photo.png"), parseAttachedFilesMarker(content))
    }

    @Test
    fun `parseAttachedFilesMarker recovers multiple comma-separated references`() {
        val content = "hello\n\n[Attached files: path1, path2, path3]"
        assertEquals(listOf("path1", "path2", "path3"), parseAttachedFilesMarker(content))
    }

    @Test
    fun `parseAttachedFilesMarker returns empty list when there is no marker`() {
        assertEquals(emptyList<String>(), parseAttachedFilesMarker("just a plain message"))
        assertEquals(emptyList<String>(), parseAttachedFilesMarker(null))
    }

    @Test
    fun `parseAttachedFilesMarker returns empty list for a marker missing its closing bracket`() {
        assertEquals(emptyList<String>(), parseAttachedFilesMarker("hi\n\n[Attached files: a.png, b.png"))
    }

    @Test
    fun `parseAttachedFilesMarker returns empty list for a marker missing the colon`() {
        assertEquals(emptyList<String>(), parseAttachedFilesMarker("hi\n\n[Attached files a.png]"))
    }

    @Test
    fun `parseAttachedFilesMarker with an empty file list inside the brackets yields an empty list`() {
        assertEquals(emptyList<String>(), parseAttachedFilesMarker("hi\n\n[Attached files: ]"))
    }

    @Test
    fun `parseAttachedFilesMarker only recovers the first marker when content has more than one`() {
        // Documents current behavior: attachedFilesMarkerCapture.find() stops at the first match,
        // so a message with two marker-shaped blocks only yields the first block's references.
        val content = "one\n\n[Attached files: a.png]\n\ntwo\n\n[Attached files: b.png]"
        assertEquals(listOf("a.png"), parseAttachedFilesMarker(content))
    }

    @Test
    fun `parseAttachedFilesMarker handles filenames with spaces and special characters`() {
        val content = "hi\n\n[Attached files: my photo (1).png, résumé #final.pdf]"
        assertEquals(listOf("my photo (1).png", "résumé #final.pdf"), parseAttachedFilesMarker(content))
    }

    @Test
    fun `ChatMessage attachmentsForDisplay prefers the structured attachments field when present`() {
        val message = ChatMessage(
            role = "user",
            content = "hi\n\n[Attached files: ignored.png]",
            attachments = listOf(MessageAttachment(name = "real.png", isImage = true)),
        )

        assertEquals(listOf(MessageAttachment(name = "real.png", isImage = true)), message.attachmentsForDisplay())
    }

    @Test
    fun `ChatMessage attachmentsForDisplay falls back to the marker when attachments is null`() {
        val message = ChatMessage(
            role = "user",
            content = "check this out\n\n[Attached files: /state/attachments/sess-1/photo.png]",
            attachments = null,
        )

        val display = message.attachmentsForDisplay()

        assertEquals(1, display.size)
        assertEquals("/state/attachments/sess-1/photo.png", display.first().path)
        assertTrue(display.first().wasBareReference)
        assertEquals("photo.png", display.first().displayFileName())
    }

    @Test
    fun `ChatMessage attachmentsForDisplay falls back to the marker when attachments is an empty list`() {
        val message = ChatMessage(
            role = "user",
            content = "hi\n\n[Attached files: a.png, b.png]",
            attachments = emptyList(),
        )

        assertEquals(2, message.attachmentsForDisplay().size)
    }

    @Test
    fun `ChatMessage attachmentsForDisplay is empty when there is neither structured data nor a marker`() {
        val message = ChatMessage(role = "assistant", content = "no attachments here", attachments = null)

        assertEquals(emptyList<MessageAttachment>(), message.attachmentsForDisplay())
    }

    @Test
    fun `ChatMessage attachmentsForDisplay does not duplicate when the marker text overlaps structured attachments`() {
        // Both the structured `attachments` field and the marker describe the same upload here --
        // attachmentsForDisplay() must return only the structured entry, not both.
        val message = ChatMessage(
            role = "user",
            content = "hi\n\n[Attached files: /state/attachments/sess-1/photo.png]",
            attachments = listOf(MessageAttachment(name = "photo.png", path = "/state/attachments/sess-1/photo.png", isImage = true)),
        )

        val display = message.attachmentsForDisplay()

        assertEquals(1, display.size)
        assertEquals("photo.png", display.first().name)
    }

    @Test
    fun `ChatMessage attachmentsForDisplay recovers every reference from multiple attached-file markers`() {
        val message = ChatMessage(
            role = "user",
            content = "hi\n\n[Attached files: a.png, b.pdf, c.jpg]",
            attachments = null,
        )

        val display = message.attachmentsForDisplay()

        assertEquals(listOf("a.png", "b.pdf", "c.jpg"), display.map { it.path })
        assertTrue(display.all { it.wasBareReference })
    }

    @Test
    fun `ChatMessage attachmentsForDisplay handles a malformed marker by producing no attachments, not a crash`() {
        val message = ChatMessage(
            role = "user",
            content = "hi\n\n[Attached files a.png]", // missing colon -- not a recognized marker
            attachments = null,
        )

        assertEquals(emptyList<MessageAttachment>(), message.attachmentsForDisplay())
    }

    @Test
    fun `capAtChatStartLimit truncates to the server-enforced 20-attachment cap`() {
        val attachments = (1..25).map { MessageAttachment(name = "file-$it.txt") }

        val capped = attachments.capAtChatStartLimit()

        assertEquals(20, capped.size)
        assertEquals("file-1.txt", capped.first().name)
        assertEquals("file-20.txt", capped.last().name)
    }

    @Test
    fun `capAtChatStartLimit is a no-op when already within the limit`() {
        val attachments = (1..5).map { MessageAttachment(name = "file-$it.txt") }

        assertEquals(5, attachments.capAtChatStartLimit().size)
    }

    // ── fileTypeIcon MIME detection ──

    @Test
    fun `fileTypeIcon maps known MIME prefixes and types to their icons`() {
        assertEquals(Icons.Filled.Image, fileTypeIcon("image/png"))
        assertEquals(Icons.Filled.Image, fileTypeIcon("image/jpeg"))
        assertEquals(Icons.Filled.Videocam, fileTypeIcon("video/mp4"))
        assertEquals(Icons.Filled.Description, fileTypeIcon("text/plain"))
        assertEquals(Icons.Filled.PictureAsPdf, fileTypeIcon("application/pdf"))
        assertEquals(Icons.Filled.Description, fileTypeIcon("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals(Icons.Filled.Description, fileTypeIcon("application/vnd.oasis.opendocument.text"))
        assertEquals(Icons.Filled.Description, fileTypeIcon("application/msword"))
    }

    @Test
    fun `fileTypeIcon falls back to a generic file icon for null or unrecognized MIME types`() {
        assertEquals(Icons.Filled.InsertDriveFile, fileTypeIcon(null))
        assertEquals(Icons.Filled.InsertDriveFile, fileTypeIcon("application/zip"))
        assertEquals(Icons.Filled.InsertDriveFile, fileTypeIcon("application/octet-stream"))
    }
}
