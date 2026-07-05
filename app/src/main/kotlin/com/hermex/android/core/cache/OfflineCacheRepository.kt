package com.hermex.android.core.cache

import com.hermex.android.core.network.dto.SessionDetail
import com.hermex.android.core.network.dto.SessionSummary

/**
 * Read-only offline cache for session-list and chat/message data, scoped by server id (see
 * [CachedSessionEntity]/[CachedMessageEntity]). [com.hermex.android.auth.AuthRepository.forgetServer]
 * calls [clearServer] so removing a server deletes its cache along with its cookies/custom headers.
 *
 * [pruneServerCache] bounds unbounded growth (see [sessionIdsToPrune] for the actual policy); it's
 * a separate, explicitly-scoped operation from [clearServer] and is never invoked as a side effect
 * of login/logout/server removal -- only ever called from an app-startup hook.
 */
interface OfflineCacheRepository {
    suspend fun cachedSessions(serverId: String): List<SessionSummary>
    suspend fun saveSessions(serverId: String, sessions: List<SessionSummary>)

    /** Null if this session has never been cached (distinct from a session with zero messages). */
    suspend fun cachedSessionDetail(serverId: String, sessionId: String): SessionDetail?

    /** Replaces this session's own cached row and its *entire* cached message list in one
     * transaction -- see [CachedMessageEntity] for why messages are always fully replaced, never
     * merged. No-ops if [detail] has no [SessionDetail.sessionId]. */
    suspend fun cacheSessionDetail(serverId: String, sessionId: String, detail: SessionDetail)

    /** Clears one session's cached transcript -- e.g. if it's ever discovered to be corrupt or
     * deleted server-side. Does not remove the session from the cached list (see [saveSessions]). */
    suspend fun clearSession(serverId: String, sessionId: String)

    /** Clears every cached session *and* every cached message for [serverId]. */
    suspend fun clearServer(serverId: String)

    /** Prunes [serverId]'s cached sessions (and sweeps any now-orphaned cached messages) under a
     * conservative retention policy -- see [sessionIdsToPrune] for exactly how [maxSessions]/
     * [maxAgeDays]/[sessionIdToPreserve] combine. Scoped entirely to [serverId]; never touches
     * another server's cache. A no-op (never throws) if [serverId] has no cached sessions. */
    suspend fun pruneServerCache(serverId: String, maxSessions: Int, maxAgeDays: Int, sessionIdToPreserve: String?)
}

/** Default used wherever no real cache is wired in -- mirrors [com.hermex.android.core.storage.NoOpCustomHeadersStore].
 * Always empty; every write is a no-op. */
object NoOpOfflineCacheRepository : OfflineCacheRepository {
    override suspend fun cachedSessions(serverId: String): List<SessionSummary> = emptyList()
    override suspend fun saveSessions(serverId: String, sessions: List<SessionSummary>) = Unit
    override suspend fun cachedSessionDetail(serverId: String, sessionId: String): SessionDetail? = null
    override suspend fun cacheSessionDetail(serverId: String, sessionId: String, detail: SessionDetail) = Unit
    override suspend fun clearSession(serverId: String, sessionId: String) = Unit
    override suspend fun clearServer(serverId: String) = Unit
    override suspend fun pruneServerCache(serverId: String, maxSessions: Int, maxAgeDays: Int, sessionIdToPreserve: String?) = Unit
}

class RoomOfflineCacheRepository(private val dao: CachedSessionDao) : OfflineCacheRepository {
    override suspend fun cachedSessions(serverId: String): List<SessionSummary> =
        dao.getSessions(serverId).map { it.toSessionSummary() }

    override suspend fun saveSessions(serverId: String, sessions: List<SessionSummary>) {
        val now = System.currentTimeMillis()
        val entities = sessions.mapNotNull { it.toCachedEntity(serverId, now) }
        dao.replaceSessions(serverId, entities)
    }

    override suspend fun cachedSessionDetail(serverId: String, sessionId: String): SessionDetail? {
        val session = dao.getSession(serverId, sessionId) ?: return null
        val messages = dao.getMessages(serverId, sessionId).map { it.toChatMessage() }
        return session.toSessionDetail(messages)
    }

    override suspend fun cacheSessionDetail(serverId: String, sessionId: String, detail: SessionDetail) {
        val now = System.currentTimeMillis()
        val sessionEntity = detail.toCachedEntity(serverId, sessionId, now)
        val messageEntities = detail.messages.orEmpty()
            .mapIndexed { index, message -> message.toCachedEntity(serverId, sessionId, index, now) }
        dao.replaceSessionDetail(sessionEntity, messageEntities)
    }

    override suspend fun clearSession(serverId: String, sessionId: String) {
        dao.deleteMessagesForSession(serverId, sessionId)
    }

    override suspend fun clearServer(serverId: String) {
        dao.deleteSessionsForServer(serverId)
        dao.deleteMessagesForServer(serverId)
    }

    override suspend fun pruneServerCache(serverId: String, maxSessions: Int, maxAgeDays: Int, sessionIdToPreserve: String?) {
        val sessions = dao.getSessions(serverId)
        val idsToPrune = sessionIdsToPrune(sessions, maxSessions, maxAgeDays, System.currentTimeMillis(), sessionIdToPreserve)
        dao.pruneSessions(serverId, idsToPrune.toList())
    }
}
