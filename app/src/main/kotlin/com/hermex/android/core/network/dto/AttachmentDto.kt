package com.hermex.android.core.network.dto

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * `POST /api/upload` response -- verified against the `hermes-webui` source at the pinned SHA
 * (`f1d399b437c1ca7fe4b6d2093aebe334c32f34a3`, see V5 Phase 5 recon). `path` is a full absolute
 * server-side path, not a bare filename. `error` is present instead of the other fields on
 * failure (the server never mixes both in one response, but every field stays nullable/optional
 * regardless per this app's tolerant-decoding rule).
 */
@Serializable
data class UploadResponse(
    val filename: String? = null,
    val path: String? = null,
    val size: Long? = null,
    val mime: String? = null,
    val isImage: Boolean? = null,
    val error: String? = null,
)

/**
 * An attachment on a [ChatMessage], or sent outbound via [ChatStartRequest.attachments]. Decoding
 * must tolerate two verified wire shapes (V5 Phase 5 recon): a full object
 * `{name, path, mime, size?, is_image?}` -- what `/api/upload` returns and what this app sends --
 * or a bare string/filename, which is what a *reloaded* session's message history returns (the
 * server persists only attachment names once a turn completes, not the full upload metadata; see
 * `_normalize_chat_attachments`/`display_attachments` server-side). [wasBareReference] records
 * which shape this instance was decoded from, for a future UI that may want to distinguish a
 * fully-known attachment from a name-only historical reference. Always encodes as the full object
 * shape -- this app never has a reason to send a bare-string attachment.
 */
@Serializable(with = MessageAttachmentSerializer::class)
data class MessageAttachment(
    val name: String? = null,
    val path: String? = null,
    val mime: String? = null,
    val size: Long? = null,
    val isImage: Boolean? = null,
    val wasBareReference: Boolean = false,
)

object MessageAttachmentSerializer : KSerializer<MessageAttachment> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): MessageAttachment {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return MessageAttachment(name = decoder.decodeString(), wasBareReference = true)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonObject -> MessageAttachment(
                // The server itself accepts `filename` as an alternate key to `name` (see
                // `_normalize_chat_attachments`) -- tolerate both on decode.
                name = (element["name"] as? JsonPrimitive)?.contentOrNull
                    ?: (element["filename"] as? JsonPrimitive)?.contentOrNull,
                path = (element["path"] as? JsonPrimitive)?.contentOrNull,
                mime = (element["mime"] as? JsonPrimitive)?.contentOrNull,
                size = (element["size"] as? JsonPrimitive)?.longOrNull,
                isImage = (element["is_image"] as? JsonPrimitive)?.booleanOrNull,
                wasBareReference = false,
            )
            is JsonPrimitive -> MessageAttachment(name = element.contentOrNull, wasBareReference = true)
            else -> MessageAttachment(wasBareReference = true)
        }
    }

    override fun serialize(encoder: Encoder, value: MessageAttachment) {
        val jsonEncoder = encoder as JsonEncoder
        val fields = buildMap {
            value.name?.let { put("name", JsonPrimitive(it)) }
            value.path?.let { put("path", JsonPrimitive(it)) }
            value.mime?.let { put("mime", JsonPrimitive(it)) }
            value.size?.let { put("size", JsonPrimitive(it)) }
            value.isImage?.let { put("is_image", JsonPrimitive(it)) }
        }
        jsonEncoder.encodeJsonElement(JsonObject(fields))
    }
}

/** Server-enforced cap (`_normalize_chat_attachments(...)[:20]`, V5 Phase 5 recon): attachments
 * beyond the first 20 are silently dropped by `/api/chat/start`. Capping client-side lets the app
 * eventually tell the user something was dropped, rather than silently losing it server-side. */
const val MAX_CHAT_START_ATTACHMENTS = 20

/** Truncates to the same limit `/api/chat/start` enforces server-side, so nothing is silently
 * dropped without the caller being able to detect it (`original.size > result.size`). */
fun List<MessageAttachment>.capAtChatStartLimit(): List<MessageAttachment> = take(MAX_CHAT_START_ATTACHMENTS)

/**
 * The `\n\n[Attached files: ...]` suffix the reference WebUI frontend (`static/messages.js`) and
 * iOS client both append to the outgoing message text -- confirmed load-bearing, not legacy: the
 * server actively parses this marker back out post-turn to match attachments onto the persisted
 * user message (`api/streaming.py`, V5 Phase 5 recon). Returns "" when [references] is empty, so
 * callers can safely append the result to a draft unconditionally.
 */
fun buildAttachedFilesMarker(references: List<String>): String {
    if (references.isEmpty()) return ""
    return "\n\n[Attached files: ${references.joinToString(", ")}]"
}

/**
 * Strips the `[Attached files: ...]` suffix (with leading blank line) from a message's display
 * text. The marker is appended client-side before send; the server echoes it back on reload.
 * Matching iOS behavior: the annotation is hidden from the user and only the attachment chips
 * (rendered separately from [ChatMessage.attachments]) are shown.
 */
fun stripAttachedFilesMarker(content: String?): String? {
    if (content == null) return null
    val markerRegex = Regex("\n\n\\[Attached files: ?.*?]", RegexOption.DOT_MATCHES_ALL)
    return content.replace(markerRegex, "")
}

/**
 * Returns a Material icon for the given MIME type. Used in both the composer's pending
 * attachment strip and in message-history attachment chips. Falls back to a generic file icon.
 */
fun fileTypeIcon(mime: String?): ImageVector {
    if (mime == null) return Icons.Filled.InsertDriveFile
    return when {
        mime.startsWith("image/") -> Icons.Filled.Image
        mime.startsWith("video/") -> Icons.Filled.Videocam
        mime.startsWith("text/") -> Icons.Filled.Description
        mime == "application/pdf" -> Icons.Filled.PictureAsPdf
        mime.startsWith("application/vnd.openxmlformats-officedocument") ||
        mime.startsWith("application/vnd.oasis.opendocument") ||
        mime.startsWith("application/msword") -> Icons.Filled.Description
        else -> Icons.Filled.InsertDriveFile
    }
}
