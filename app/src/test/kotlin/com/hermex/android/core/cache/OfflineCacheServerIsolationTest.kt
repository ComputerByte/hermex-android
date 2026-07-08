package com.hermex.android.core.cache

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OfflineCacheServerIsolationTest {
    @Test
    fun fake_repo_server_scoping() = runBlocking {
        val repo = FakeOfflineCacheRepository()

        // Save sessions for server A
        val serverA = "server-a"
        repo.saveSessions(serverA, listOf(
            sampleSession("a1", "Server A Session"),
        ))

        // Save sessions for server B
        val serverB = "server-b"
        repo.saveSessions(serverB, listOf(
            sampleSession("b1", "Server B Session"),
        ))

        // Server A's data should not leak to server B
        val aSessions = repo.cachedSessions(serverA)
        val bSessions = repo.cachedSessions(serverB)
        assertEquals(1, aSessions.size)
        assertEquals(1, bSessions.size)
        assertNotEquals(aSessions[0].sessionId, bSessions[0].sessionId)
    }

    @Test
    fun clearServer_removes_only_that_servers_data() = runBlocking {
        val repo = FakeOfflineCacheRepository()
        val serverA = "server-a"
        val serverB = "server-b"

        repo.saveSessions(serverA, listOf(sampleSession("a1", "A1")))
        repo.saveSessions(serverB, listOf(sampleSession("b1", "B1")))

        // Clear server A
        repo.clearServer(serverA)

        // Server A should be empty
        assertEquals(0, repo.cachedSessions(serverA).size)
        // Server B should still have its data
        assertEquals(1, repo.cachedSessions(serverB).size)
    }

    @Test
    fun clearServer_is_idempotent() = runBlocking {
        val repo = FakeOfflineCacheRepository()
        val serverA = "server-a"
        // Clear non-existent server should not throw
        repo.clearServer(serverA)
        repo.clearServer(serverA)
        assertEquals(0, repo.cachedSessions(serverA).size)
    }

    private fun sampleSession(id: String, title: String) =
        com.hermex.android.core.network.dto.SessionSummary(
            sessionId = id,
            title = title,
            createdAt = 0.0,
            updatedAt = 0.0,
        )
}