package com.hermex.android.chat

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** A picked file's bytes and display metadata, read from a content [Uri] -- see
 * [AttachmentFileReader] for why the reader is abstracted behind an interface rather than
 * [ChatViewModel] touching [android.content.ContentResolver] directly. */
class AttachmentFile(
    val name: String,
    val bytes: ByteArray,
    val mime: String?,
)

sealed class AttachmentReadResult {
    data class Success(val file: AttachmentFile) : AttachmentReadResult()

    /** [name] is best-effort display metadata even on failure, so the error message can name the
     * offending file. */
    data class TooLarge(val name: String) : AttachmentReadResult()
    data object Unreadable : AttachmentReadResult()
}

/** Reads a picked file's bytes + metadata from a content [Uri] -- the one piece of the
 * attachment feature that genuinely needs `android.content.ContentResolver`. Abstracted so
 * [ChatViewModel] itself stays plain-JVM-testable: `Uri.parse`/`Uri.EMPTY` both return `null`
 * under this project's unit-test stubbing (no Robolectric), so tests exercise the
 * network/state logic via `ChatViewModel.performAttachmentUpload(AttachmentFile)` directly
 * instead of ever constructing a real `Uri`. */
fun interface AttachmentFileReader {
    suspend fun read(uri: Uri): AttachmentReadResult
}

/** Real implementation. Reads the whole file into memory, bounded by [maxBytes] so a huge pick
 * can't balloon app memory before the server even gets a chance to reject it with its own size
 * limit -- the server's default is 20 MB (`HERMES_WEBUI_MAX_UPLOAD_MB`, V5 Phase 5 recon) but is
 * deployment-configurable, so this is a client-side sanity cap, not a substitute for handling the
 * server's own error response. */
class ContentResolverAttachmentReader(
    private val contentResolver: ContentResolver,
    private val maxBytes: Long = DEFAULT_MAX_ATTACHMENT_BYTES,
) : AttachmentFileReader {
    override suspend fun read(uri: Uri): AttachmentReadResult {
        val name = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
        val knownSize = querySize(uri)
        if (knownSize != null && knownSize > maxBytes) return AttachmentReadResult.TooLarge(name)

        val bytes = try {
            contentResolver.openInputStream(uri)?.use { readBounded(it, maxBytes) }
        } catch (e: Exception) {
            null
        } ?: return AttachmentReadResult.Unreadable

        // The stream may not have reported an accurate Content-Length up front -- re-check the
        // bytes actually read, not just the cursor's claimed size.
        if (bytes.size.toLong() > maxBytes) return AttachmentReadResult.TooLarge(name)

        val mime = contentResolver.getType(uri) ?: guessMimeFromExtension(name)
        return AttachmentReadResult.Success(AttachmentFile(name = name, bytes = bytes, mime = mime))
    }

    private fun queryDisplayName(uri: Uri): String? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private fun querySize(uri: Uri): Long? = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
        }

    /** Stops reading (and returns what it has, one buffer past the limit) as soon as [limit] is
     * exceeded, rather than buffering an arbitrarily large stream into memory only to reject it
     * afterward -- the caller re-checks the final size against [limit] regardless. */
    private fun readBounded(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
            if (total > limit) break
        }
        return output.toByteArray()
    }

    private fun guessMimeFromExtension(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension.ifEmpty { null }?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    }

    companion object {
        const val DEFAULT_MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024
    }
}
