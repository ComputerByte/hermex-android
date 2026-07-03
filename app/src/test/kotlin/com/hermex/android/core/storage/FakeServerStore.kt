package com.hermex.android.core.storage

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Shared in-memory [ServerStore] test double -- avoids needing a real Android Context/DataStore.
 * The single-arg constructor keeps the many ViewModel tests that only care about "one server
 * already configured and active at this URL" (not server-store CRUD itself) terse; pass no url
 * for a store with nothing configured yet. */
internal class FakeServerStore(initialUrl: String? = null) : ServerStore {
    private val _state = MutableStateFlow(
        if (initialUrl == null) {
            ServerStoreState()
        } else {
            val config = HermexServerConfig(
                id = UUID.randomUUID().toString(),
                name = "Test Server",
                baseUrl = initialUrl,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
            )
            ServerStoreState(servers = listOf(config), activeServerId = config.id)
        },
    )
    override val state: StateFlow<ServerStoreState> = _state

    override suspend fun load(): ServerStoreState = _state.value

    override suspend fun addServer(name: String, baseUrl: String, id: String): HermexServerConfig {
        val config = HermexServerConfig(
            id = id,
            name = name,
            baseUrl = baseUrl,
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )
        _state.update { it.copy(servers = it.servers + config, activeServerId = it.activeServerId ?: config.id) }
        return config
    }

    override suspend fun updateServer(id: String, name: String, baseUrl: String): HermexServerConfig {
        val existing = _state.value.servers.find { it.id == id }
            ?: return HermexServerConfig(id, name, baseUrl, 0L, 0L)
        val updatedConfig = existing.copy(name = name, baseUrl = baseUrl, updatedAtEpochMillis = 1L)
        _state.update { current -> current.copy(servers = current.servers.map { if (it.id == id) updatedConfig else it }) }
        return updatedConfig
    }

    override suspend fun removeServer(id: String) {
        _state.update { current ->
            val remaining = current.servers.filter { it.id != id }
            val activeId = if (current.activeServerId == id) remaining.firstOrNull()?.id else current.activeServerId
            current.copy(servers = remaining, activeServerId = activeId)
        }
    }

    override suspend fun setActiveServer(id: String) {
        _state.update { current -> if (current.servers.none { it.id == id }) current else current.copy(activeServerId = id) }
    }

    override fun activeServerSnapshot(): HermexServerConfig? = _state.value.activeServer
}
