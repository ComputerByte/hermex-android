package com.hermex.android.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hermex.android.core.network.HermexJson
import com.hermex.android.core.storage.ServerCollectionOps.selfHealed
import com.hermex.android.core.storage.ServerCollectionOps.withActiveServer
import com.hermex.android.core.storage.ServerCollectionOps.withAddedServer
import com.hermex.android.core.storage.ServerCollectionOps.withRemovedServer
import com.hermex.android.core.storage.ServerCollectionOps.withUpdatedServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.serverDataStore by preferencesDataStore(name = "hermex_server")

/**
 * Thin Context/DataStore wrapper -- every actual decision (legacy migration, self-healing,
 * add/update/remove/switch semantics) lives in the pure, unit-testable [ServerCollectionOps].
 * Decode failures fail safe to an empty collection rather than crashing.
 */
class DataStoreServerStore(private val context: Context) : ServerStore {
    private val legacyKey = stringPreferencesKey("server_url")
    private val collectionKey = stringPreferencesKey("server_collection_json")

    private val _state = MutableStateFlow(ServerStoreState())
    override val state: StateFlow<ServerStoreState> = _state.asStateFlow()

    override fun activeServerSnapshot(): HermexServerConfig? = _state.value.activeServer

    override suspend fun load(): ServerStoreState {
        val prefs = context.serverDataStore.data.firstOrNull()
        val decoded = prefs?.get(collectionKey)?.let {
            runCatching { HermexJson.decodeFromString<ServerStoreState>(it) }.getOrNull()
        }

        val resolved: ServerStoreState
        if (decoded != null) {
            resolved = decoded.selfHealed()
            if (resolved != decoded) persist(resolved)
        } else {
            resolved = ServerCollectionOps.migrateLegacy(prefs?.get(legacyKey))
            // Only persist a real migration result -- a totally fresh install (no legacy key
            // either) would otherwise write an empty collection to disk on every single load().
            if (resolved.servers.isNotEmpty()) persist(resolved)
        }

        _state.value = resolved
        return resolved
    }

    override suspend fun addServer(name: String, baseUrl: String, id: String): HermexServerConfig {
        val (updated, config) = load().withAddedServer(name, baseUrl, id)
        persist(updated)
        _state.value = updated
        return config
    }

    override suspend fun updateServer(id: String, name: String, baseUrl: String): HermexServerConfig {
        val (updated, config) = load().withUpdatedServer(id, name, baseUrl)
        persist(updated)
        _state.value = updated
        return config
    }

    override suspend fun removeServer(id: String) {
        val updated = load().withRemovedServer(id)
        persist(updated)
        _state.value = updated
    }

    override suspend fun setActiveServer(id: String) {
        val updated = load().withActiveServer(id)
        persist(updated)
        _state.value = updated
    }

    private suspend fun persist(newState: ServerStoreState) {
        context.serverDataStore.edit { prefs -> prefs[collectionKey] = HermexJson.encodeToString(newState) }
    }
}
