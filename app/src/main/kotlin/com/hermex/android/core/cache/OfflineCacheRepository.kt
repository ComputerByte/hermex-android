package com.hermex.android.core.cache

import com.hermex.android.core.network.dto.SessionSummary

/**
 * Read-only offline cache for session-list data, scoped by server id (see [CachedSessionEntity]).
 * [com.hermex.android.auth.AuthRepository.forgetServer] calls [clearServer] so removing a server
 * deletes its cache along with its cookies/custom headers.
 *
 * TODO: no pruning policy yet -- cached sessions live until their server is removed
 * ([clearServer]) or overwritten by a newer [saveSessions] call. Fine for MVP data volumes (one
 * row per session, no message bodies); revisit with a max-age or max-row cap if that changes.
 */
interface OfflineCacheRepository {
    suspend fun cachedSessions(serverId: String): List<SessionSummary>
    suspend fun saveSessions(serverId: String, sessions: List<SessionSummary>)
    suspend fun clearServer(serverId: String)
}

/** Default used wherever no real cache is wired in -- mirrors [com.hermex.android.core.storage.NoOpCustomHeadersStore].
 * Always empty; every write is a no-op. */
object NoOpOfflineCacheRepository : OfflineCacheRepository {
    override suspend fun cachedSessions(serverId: String): List<SessionSummary> = emptyList()
    override suspend fun saveSessions(serverId: String, sessions: List<SessionSummary>) = Unit
    override suspend fun clearServer(serverId: String) = Unit
}

class RoomOfflineCacheRepository(private val dao: CachedSessionDao) : OfflineCacheRepository {
    override suspend fun cachedSessions(serverId: String): List<SessionSummary> =
        dao.getSessions(serverId).map { it.toSessionSummary() }

    override suspend fun saveSessions(serverId: String, sessions: List<SessionSummary>) {
        val now = System.currentTimeMillis()
        val entities = sessions.mapNotNull { it.toCachedEntity(serverId, now) }
        dao.replaceSessions(serverId, entities)
    }

    override suspend fun clearServer(serverId: String) {
        dao.deleteSessionsForServer(serverId)
    }
}
