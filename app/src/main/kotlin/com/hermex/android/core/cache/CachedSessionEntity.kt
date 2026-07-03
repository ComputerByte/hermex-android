package com.hermex.android.core.cache

import androidx.room.Entity
import androidx.room.Index
import com.hermex.android.core.network.dto.SessionSummary

/**
 * An offline snapshot of one [SessionSummary], scoped by [serverId] so switching (or removing) a
 * server can never mix or leak another server's cached sessions -- every read/write in
 * [CachedSessionDao] takes [serverId] explicitly.
 *
 * Deliberately omits fields that are meaningless once cached: [SessionSummary.activeStreamId] and
 * `isStreaming` describe a live server-side state that can't be "offline" (a cached session was
 * never mid-stream by definition), and `isCliSession`/`sourceTag`/`sessionSource`/`sourceLabel`
 * aren't shown anywhere in the session list UI. Never stores cookies, custom headers, or any
 * other secret -- those already have their own scoped stores (see [com.hermex.android.core.storage.CookieStore],
 * [com.hermex.android.core.storage.CustomHeadersStore]).
 */
@Entity(tableName = "cached_sessions", primaryKeys = ["serverId", "sessionId"], indices = [Index("serverId")])
data class CachedSessionEntity(
    val serverId: String,
    val sessionId: String,
    val title: String?,
    val workspace: String?,
    val model: String?,
    val modelProvider: String?,
    val messageCount: Int?,
    val createdAt: Double?,
    val updatedAt: Double?,
    val lastMessageAt: Double?,
    val pinned: Boolean?,
    val archived: Boolean?,
    val projectId: String?,
    val profile: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val estimatedCost: Double?,
    /** When this row was last written -- not currently surfaced in the UI or used to prune
     * (see the TODO on [OfflineCacheRepository] about cache retention), but recorded now so a
     * pruning policy can be added later without a schema migration. */
    val cachedAtEpochMillis: Long,
)

/** Requires a non-null [SessionSummary.sessionId] -- a session with no id can't be looked up or
 * opened again anyway, so it's not worth caching. */
fun SessionSummary.toCachedEntity(serverId: String, cachedAtEpochMillis: Long): CachedSessionEntity? {
    val id = sessionId ?: return null
    return CachedSessionEntity(
        serverId = serverId,
        sessionId = id,
        title = title,
        workspace = workspace,
        model = model,
        modelProvider = modelProvider,
        messageCount = messageCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastMessageAt = lastMessageAt,
        pinned = pinned,
        archived = archived,
        projectId = projectId,
        profile = profile,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        estimatedCost = estimatedCost,
        cachedAtEpochMillis = cachedAtEpochMillis,
    )
}

fun CachedSessionEntity.toSessionSummary(): SessionSummary = SessionSummary(
    sessionId = sessionId,
    title = title,
    workspace = workspace,
    model = model,
    modelProvider = modelProvider,
    messageCount = messageCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastMessageAt = lastMessageAt,
    pinned = pinned,
    archived = archived,
    projectId = projectId,
    profile = profile,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    estimatedCost = estimatedCost,
)
