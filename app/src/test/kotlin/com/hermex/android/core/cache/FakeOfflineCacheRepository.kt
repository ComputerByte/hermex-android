package com.hermex.android.core.cache

import com.hermex.android.core.network.dto.SessionDetail
import com.hermex.android.core.network.dto.SessionSummary

/** Shared in-memory [OfflineCacheRepository] test double -- avoids needing a real Room database
 * (there's no Robolectric/instrumented test setup in this project's JVM test source set). */
internal class FakeOfflineCacheRepository : OfflineCacheRepository {
    private val sessionsByServer = mutableMapOf<String, List<SessionSummary>>()
    private val detailsByServerAndSession = mutableMapOf<Pair<String, String>, SessionDetail>()

    /** Mirrors [CachedSessionEntity.cachedAtEpochMillis] -- tracked separately since
     * [SessionSummary] (the plain DTO) has no cache-timestamp field of its own. Only entries with
     * a recorded timestamp are ever considered by [pruneServerCache], matching how a real
     * `cached_sessions` row always carries one. */
    private val cachedAtByServerAndSession = mutableMapOf<Pair<String, String>, Long>()

    override suspend fun cachedSessions(serverId: String): List<SessionSummary> = sessionsByServer[serverId].orEmpty()

    override suspend fun saveSessions(serverId: String, sessions: List<SessionSummary>) {
        sessionsByServer[serverId] = sessions
        val now = System.currentTimeMillis()
        sessions.forEach { session ->
            val id = session.sessionId ?: return@forEach
            cachedAtByServerAndSession[serverId to id] = now
        }
    }

    override suspend fun cachedSessionDetail(serverId: String, sessionId: String): SessionDetail? =
        detailsByServerAndSession[serverId to sessionId]

    override suspend fun cacheSessionDetail(serverId: String, sessionId: String, detail: SessionDetail) {
        detailsByServerAndSession[serverId to sessionId] = detail
        cachedAtByServerAndSession[serverId to sessionId] = System.currentTimeMillis()
    }

    override suspend fun clearSession(serverId: String, sessionId: String) {
        detailsByServerAndSession.remove(serverId to sessionId)
    }

    override suspend fun clearServer(serverId: String) {
        sessionsByServer.remove(serverId)
        detailsByServerAndSession.keys.filter { it.first == serverId }.forEach { detailsByServerAndSession.remove(it) }
        cachedAtByServerAndSession.keys.filter { it.first == serverId }.forEach { cachedAtByServerAndSession.remove(it) }
    }

    /** Reuses [sessionIdsToPrune] -- the exact same decision function [RoomOfflineCacheRepository]
     * calls -- against synthetic [CachedSessionEntity] rows built from this fake's own state, so
     * the policy under test here is identical to production, not a re-implementation of it. Then
     * sweeps any cached detail (this fake's stand-in for "cached messages") whose session id no
     * longer appears in the post-prune session list for [serverId] -- mirroring
     * [CachedSessionDao.deleteOrphanedMessagesForServer], which also catches sessions dropped by
     * [saveSessions] outside of pruning, not just ones this call itself decided to prune. */
    override suspend fun pruneServerCache(serverId: String, maxSessions: Int, maxAgeDays: Int, sessionIdToPreserve: String?) {
        val sessions = sessionsByServer[serverId].orEmpty()
        val entities = sessions.mapNotNull { session ->
            val id = session.sessionId ?: return@mapNotNull null
            val cachedAt = cachedAtByServerAndSession[serverId to id] ?: return@mapNotNull null
            session.toCachedEntity(serverId, cachedAt)
        }
        val idsToPrune = sessionIdsToPrune(entities, maxSessions, maxAgeDays, System.currentTimeMillis(), sessionIdToPreserve)
        if (idsToPrune.isNotEmpty()) {
            sessionsByServer[serverId] = sessions.filterNot { it.sessionId in idsToPrune }
        }

        val survivingIds = sessionsByServer[serverId].orEmpty().mapNotNull { it.sessionId }.toSet()
        detailsByServerAndSession.keys.filter { it.first == serverId && it.second !in survivingIds }
            .forEach { detailsByServerAndSession.remove(it) }
        cachedAtByServerAndSession.keys.filter { it.first == serverId && it.second !in survivingIds }
            .forEach { cachedAtByServerAndSession.remove(it) }
    }
}
