package com.hermex.android.core.storage

import com.hermex.android.core.storage.ServerCollectionOps.selfHealed
import com.hermex.android.core.storage.ServerCollectionOps.withActiveServer
import com.hermex.android.core.storage.ServerCollectionOps.withAddedServer
import com.hermex.android.core.storage.ServerCollectionOps.withRemovedServer
import com.hermex.android.core.storage.ServerCollectionOps.withUpdatedServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun config(id: String, baseUrl: String = "https://$id.example.com/") =
    HermexServerConfig(id = id, name = id, baseUrl = baseUrl, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L)

class ServerCollectionOpsTest {
    @Test
    fun `an empty store has no servers and no active server`() {
        val state = ServerStoreState()
        assertTrue(state.servers.isEmpty())
        assertNull(state.activeServerId)
        assertNull(state.activeServer)
    }

    @Test
    fun `adding the first server creates it with the given name and url and makes it active`() {
        val (updated, config) = ServerStoreState().withAddedServer("Mac Mini", "https://hermex.local/")

        assertEquals(1, updated.servers.size)
        assertTrue(config.id.isNotBlank())
        assertEquals("Mac Mini", config.name)
        assertEquals("https://hermex.local/", config.baseUrl)
        assertEquals(config.id, updated.activeServerId)
    }

    @Test
    fun `adding a second server does not steal active focus from the first`() {
        val (afterFirst, first) = ServerStoreState().withAddedServer("A", "https://a.example.com/")
        val (afterSecond, second) = afterFirst.withAddedServer("B", "https://b.example.com/")

        assertEquals(2, afterSecond.servers.size)
        assertEquals(first.id, afterSecond.activeServerId)
        assertTrue(afterSecond.servers.any { it.id == second.id })
    }

    @Test
    fun `setActiveServer changes which server is active`() {
        val (afterFirst, first) = ServerStoreState().withAddedServer("A", "https://a.example.com/")
        val (state, second) = afterFirst.withAddedServer("B", "https://b.example.com/")

        val updated = state.withActiveServer(second.id)

        assertEquals(second.id, updated.activeServerId)
        assertTrue(updated.servers.any { it.id == first.id }) // first server still configured
    }

    @Test
    fun `setActiveServer with an unknown id is a no-op`() {
        val (state, first) = ServerStoreState().withAddedServer("A", "https://a.example.com/")

        val updated = state.withActiveServer("does-not-exist")

        assertEquals(first.id, updated.activeServerId)
    }

    @Test
    fun `updateServer edits name and url but keeps the id stable`() {
        val (state, original) = ServerStoreState().withAddedServer("A", "https://a.example.com/")

        val (updated, edited) = state.withUpdatedServer(original.id, "Renamed", "https://renamed.example.com/")

        assertEquals(original.id, edited.id)
        assertEquals("Renamed", edited.name)
        assertEquals("https://renamed.example.com/", edited.baseUrl)
        assertEquals(1, updated.servers.size)
        assertEquals(original.id, updated.servers.single().id)
    }

    @Test
    fun `updateServer with an unknown id does not crash and leaves the collection unchanged`() {
        val (state, original) = ServerStoreState().withAddedServer("A", "https://a.example.com/")

        val (updated, _) = state.withUpdatedServer("does-not-exist", "New Name", "https://new.example.com/")

        assertEquals(state, updated)
    }

    @Test
    fun `removing an inactive server leaves the active server unchanged`() {
        val (afterFirst, first) = ServerStoreState().withAddedServer("A", "https://a.example.com/")
        val (state, second) = afterFirst.withAddedServer("B", "https://b.example.com/")

        val updated = state.withRemovedServer(second.id)

        assertEquals(first.id, updated.activeServerId)
        assertEquals(1, updated.servers.size)
    }

    @Test
    fun `removing the active server selects another remaining server`() {
        val (afterFirst, first) = ServerStoreState().withAddedServer("A", "https://a.example.com/")
        val (state, second) = afterFirst.withAddedServer("B", "https://b.example.com/")

        val updated = state.withRemovedServer(first.id)

        assertEquals(second.id, updated.activeServerId)
        assertEquals(listOf(second.id), updated.servers.map { it.id })
    }

    @Test
    fun `removing the last server leaves no active server`() {
        val (state, only) = ServerStoreState().withAddedServer("A", "https://a.example.com/")

        val updated = state.withRemovedServer(only.id)

        assertTrue(updated.servers.isEmpty())
        assertNull(updated.activeServerId)
    }

    @Test
    fun `a legacy single server url migrates into one server config and becomes active`() {
        val migrated = ServerCollectionOps.migrateLegacy("https://hermes.example.com/")

        assertEquals(1, migrated.servers.size)
        val config = migrated.servers.single()
        assertEquals("https://hermes.example.com/", config.baseUrl)
        assertEquals("hermes.example.com", config.name)
        assertEquals(config.id, migrated.activeServerId)
    }

    @Test
    fun `migrating with no legacy url produces an empty collection rather than crashing`() {
        assertEquals(ServerStoreState(), ServerCollectionOps.migrateLegacy(null))
        assertEquals(ServerStoreState(), ServerCollectionOps.migrateLegacy(""))
    }

    @Test
    fun `an invalid activeServerId self-heals to the first configured server`() {
        val broken = ServerStoreState(servers = listOf(config("a"), config("b")), activeServerId = "does-not-exist")

        val healed = broken.selfHealed()

        assertEquals("a", healed.activeServerId)
    }

    @Test
    fun `a valid activeServerId is left untouched by self-healing`() {
        val state = ServerStoreState(servers = listOf(config("a"), config("b")), activeServerId = "b")

        assertEquals(state, state.selfHealed())
    }

    @Test
    fun `self-healing an empty server list with a stale activeServerId clears it`() {
        val broken = ServerStoreState(servers = emptyList(), activeServerId = "does-not-exist")

        val healed = broken.selfHealed()

        assertNull(healed.activeServerId)
    }
}
