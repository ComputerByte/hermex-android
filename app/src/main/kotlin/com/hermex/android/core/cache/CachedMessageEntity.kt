package com.hermex.android.core.cache

import androidx.room.Entity
import androidx.room.Index
import com.hermex.android.core.network.dto.ChatMessage

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
    val cachedAtEpochMillis: Long,
)

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
)
