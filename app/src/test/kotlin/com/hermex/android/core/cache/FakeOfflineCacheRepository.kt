package com.hermex.android.core.cache

import com.hermex.android.core.network.dto.SessionDetail
import com.hermex.android.core.network.dto.SessionSummary

/** Shared in-memory [OfflineCacheRepository] test double -- avoids needing a real Room database
 * (there's no Robolectric/instrumented test setup in this project's JVM test source set). */
internal class FakeOfflineCacheRepository : OfflineCacheRepository {
    private val sessionsByServer = mutableMapOf<String, List<SessionSummary>>()
    private val detailsByServerAndSession = mutableMapOf<Pair<String, String>, SessionDetail>()

    override suspend fun cachedSessions(serverId: String): List<SessionSummary> = sessionsByServer[serverId].orEmpty()

    override suspend fun saveSessions(serverId: String, sessions: List<SessionSummary>) {
        sessionsByServer[serverId] = sessions
    }

    override suspend fun cachedSessionDetail(serverId: String, sessionId: String): SessionDetail? =
        detailsByServerAndSession[serverId to sessionId]

    override suspend fun cacheSessionDetail(serverId: String, sessionId: String, detail: SessionDetail) {
        detailsByServerAndSession[serverId to sessionId] = detail
    }

    override suspend fun clearSession(serverId: String, sessionId: String) {
        detailsByServerAndSession.remove(serverId to sessionId)
    }

    override suspend fun clearServer(serverId: String) {
        sessionsByServer.remove(serverId)
        detailsByServerAndSession.keys.filter { it.first == serverId }.forEach { detailsByServerAndSession.remove(it) }
    }
}
