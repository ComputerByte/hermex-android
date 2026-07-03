package com.hermex.android.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull

private val Context.cookieDataStore by preferencesDataStore(name = "hermex_cookies")

/**
 * Cookies are scoped by [serverId] (not host -- a user can edit a server's baseUrl without
 * losing its identity) so switching the active server, or holding several configured at once,
 * never leaks one server's session cookie into another's requests. Pre-multi-server installs had
 * a single cookie blob under an unscoped key; the first [load] for a given [serverId] migrates
 * that blob in (see [migrateLegacyIfNeeded]) -- in practice only the one server created by
 * [DataStoreServerStore]'s legacy-URL migration will ever find it, since any later `serverId` is
 * a fresh UUID with no relationship to the legacy key, and the migrated server's own cookie store
 * is always the first (and only) one asked to load right after that migration.
 */
class DataStoreCookieStore(private val context: Context, private val serverId: String) : CookieStore {
    private val legacyKey = stringPreferencesKey("cookie_jar_json")
    private val key = stringPreferencesKey("cookie_jar_json_$serverId")

    override suspend fun save(serializedCookies: String) {
        context.cookieDataStore.edit { prefs -> prefs[key] = serializedCookies }
    }

    override suspend fun load(): String? {
        migrateLegacyIfNeeded()
        return context.cookieDataStore.data.firstOrNull()?.get(key)
    }

    override suspend fun clear() {
        context.cookieDataStore.edit { prefs -> prefs.remove(key) }
    }

    /** Copies the legacy (unscoped) cookie blob into this server's scoped key the first time
     * it's asked for, then removes the legacy key so it only ever runs once. No-op if there's
     * nothing to migrate, or if this server's scoped key is already populated. */
    private suspend fun migrateLegacyIfNeeded() {
        context.cookieDataStore.edit { prefs ->
            if (prefs.contains(key)) return@edit
            val legacy = prefs[legacyKey] ?: return@edit
            prefs[key] = legacy
            prefs.remove(legacyKey)
        }
    }
}
