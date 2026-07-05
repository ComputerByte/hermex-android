package com.hermex.android

import com.hermex.android.core.cache.DEFAULT_MAX_CACHED_SESSIONS_PER_SERVER
import com.hermex.android.core.cache.FakeOfflineCacheRepository
import com.hermex.android.core.network.dto.SessionSummary
import com.hermex.android.core.storage.HermexServerConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers [pruneAllServerCaches] -- the startup-hook loop [AppContainer.pruneOfflineCaches]
 * delegates to. Extracted as a standalone function specifically so this is testable without a
 * real Android [android.content.Context] the way constructing an [AppContainer] would require. */
class StartupCachePruningTest {
    private fun server(id: String) = HermexServerConfig(id = id, name = id, baseUrl = "https://$id", createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L)

    private fun summary(id: String, lastMessageAt: Double? = null) = SessionSummary(sessionId = id, lastMessageAt = lastMessageAt)

    @Test
    fun `prunes every configured server down to the default session cap`() = runTest {
        val cache = FakeOfflineCacheRepository()
        cache.saveSessions("server-a", (1..80).map { summary("a$it", lastMessageAt = it.toDouble()) })
        cache.saveSessions("server-b", (1..80).map { summary("b$it", lastMessageAt = it.toDouble()) })

        pruneAllServerCaches(listOf(server("server-a"), server("server-b")), cache)

        assertEquals(DEFAULT_MAX_CACHED_SESSIONS_PER_SERVER, cache.cachedSessions("server-a").size)
        assertEquals(DEFAULT_MAX_CACHED_SESSIONS_PER_SERVER, cache.cachedSessions("server-b").size)
    }

    @Test
    fun `a server under the default cap is left untouched`() = runTest {
        val cache = FakeOfflineCacheRepository()
        cache.saveSessions("server-a", (1..5).map { summary("a$it", lastMessageAt = it.toDouble()) })

        pruneAllServerCaches(listOf(server("server-a")), cache)

        assertEquals(5, cache.cachedSessions("server-a").size)
    }

    @Test
    fun `an empty server list prunes nothing and does not crash`() = runTest {
        val cache = FakeOfflineCacheRepository()

        pruneAllServerCaches(emptyList(), cache)
    }

    @Test
    fun `only servers passed in are touched -- an unconfigured server id is never pruned`() = runTest {
        val cache = FakeOfflineCacheRepository()
        cache.saveSessions("known", (1..80).map { summary("k$it", lastMessageAt = it.toDouble()) })
        cache.saveSessions("not-configured", (1..80).map { summary("u$it", lastMessageAt = it.toDouble()) })

        pruneAllServerCaches(listOf(server("known")), cache)

        assertEquals(DEFAULT_MAX_CACHED_SESSIONS_PER_SERVER, cache.cachedSessions("known").size)
        assertEquals(80, cache.cachedSessions("not-configured").size) // never in the servers list, never touched
    }
}
