package com.hermex.android.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull

private val Context.serverDataStore by preferencesDataStore(name = "hermex_server")

class DataStoreServerStore(private val context: Context) : ServerStore {
    private val key = stringPreferencesKey("server_url")

    override suspend fun save(serverUrl: String) {
        context.serverDataStore.edit { prefs -> prefs[key] = serverUrl }
    }

    override suspend fun load(): String? =
        context.serverDataStore.data.firstOrNull()?.get(key)

    override suspend fun clear() {
        context.serverDataStore.edit { prefs -> prefs.remove(key) }
    }
}
