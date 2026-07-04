package com.hermex.android.core.network.dto

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
}
