package com.hermex.android.core.cache

import com.hermex.android.core.network.dto.SessionSummary

/** Shared in-memory [OfflineCacheRepository] test double -- avoids needing a real Room database
 * (there's no Robolectric/instrumented test setup in this project's JVM test source set). */
internal class FakeOfflineCacheRepository : OfflineCacheRepository {
    private val sessionsByServer = mutableMapOf<String, List<SessionSummary>>()

    override suspend fun cachedSessions(serverId: String): List<SessionSummary> = sessionsByServer[serverId].orEmpty()

    override suspend fun saveSessions(serverId: String, sessions: List<SessionSummary>) {
        sessionsByServer[serverId] = sessions
    }

    override suspend fun clearServer(serverId: String) {
        sessionsByServer.remove(serverId)
    }
}
