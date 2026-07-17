package com.hermex.android.core.cache

import androidx.room.Entity
import androidx.room.Index
import com.hermex.android.core.network.HermexJson
import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.core.network.dto.MessageAttachment
import com.hermex.android.core.network.dto.MessageAttachmentSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * An offline snapshot of one [ChatMessage] within a session's transcript, scoped by [serverId] +
 * [sessionId] (see [CachedSessionEntity] for why [serverId] is never derived from the URL).
 *
 * [orderIndex] (the message's position in the transcript when it was cached), not a server
 * message id, is part of the primary key: the server doesn't always assign [ChatMessage.messageId]
 * (see [ChatMessage.stableId]'s own fallback), so there's nothing reliably stable to key on
 * instead. This is safe specifically because every cache write replaces the *entire* message list
 * for a session in one transaction ([CachedSessionDao.replaceMessagesForSession]) -- there's never
 * a partial merge by id that an unstable key could corrupt.
 */
@Entity(
    tableName = "cached_messages",
    primaryKeys = ["serverId", "sessionId", "orderIndex"],
    indices = [Index(value = ["serverId", "sessionId"])],
)
data class CachedMessageEntity(
    val serverId: String,
    val sessionId: String,
    val orderIndex: Int,
    val role: String?,
    val content: String?,
    val reasoning: String?,
    val timestamp: Double?,
    val messageId: String?,
    val name: String?,
    val toolCallId: String?,
    /** JSON keeps the server's tolerant full-object/bare-name attachment shapes intact without
     * adding a second Room table for a list that is always replaced with its parent message. */
    val attachmentsJson: String?,
    val cachedAtEpochMillis: Long,
)

private val messageAttachmentListSerializer = ListSerializer(MessageAttachmentSerializer)

private fun encodeAttachments(attachments: List<MessageAttachment>?): String? =
    attachments?.let { values ->
        // Preserve the server's historical bare-filename shape so wasBareReference survives an
        // offline-cache round trip. Fresh/full attachments continue using the wire serializer.
        val elements = values.map { attachment ->
            if (
                attachment.wasBareReference &&
                attachment.name != null &&
                attachment.path == null &&
                attachment.mime == null &&
                attachment.size == null &&
                attachment.isImage == null
            ) {
                JsonPrimitive(attachment.name)
            } else {
                HermexJson.encodeToJsonElement(MessageAttachmentSerializer, attachment)
            }
        }
        HermexJson.encodeToString(JsonElement.serializer(), JsonArray(elements))
    }

private fun decodeAttachments(json: String?): List<MessageAttachment>? =
    json?.let { runCatching { HermexJson.decodeFromString(messageAttachmentListSerializer, it) }.getOrNull() }

fun ChatMessage.toCachedEntity(
    serverId: String,
    sessionId: String,
    orderIndex: Int,
    cachedAtEpochMillis: Long,
): CachedMessageEntity = CachedMessageEntity(
    serverId = serverId,
    sessionId = sessionId,
    orderIndex = orderIndex,
    role = role,
    content = content,
    reasoning = reasoning,
    timestamp = effectiveTimestamp,
    messageId = messageId,
    name = name,
    toolCallId = toolCallId,
    attachmentsJson = encodeAttachments(attachments),
    cachedAtEpochMillis = cachedAtEpochMillis,
)

fun CachedMessageEntity.toChatMessage(): ChatMessage = ChatMessage(
    role = role,
    content = content,
    timestamp = timestamp,
    messageId = messageId,
    name = name,
    toolCallId = toolCallId,
    reasoning = reasoning,
    attachments = decodeAttachments(attachmentsJson),
)
