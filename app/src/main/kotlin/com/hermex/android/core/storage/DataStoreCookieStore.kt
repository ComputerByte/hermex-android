package com.hermex.android.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull

private val Context.cookieDataStore by preferencesDataStore(name = "hermex_cookies")

class DataStoreCookieStore(private val context: Context) : CookieStore {
    private val key = stringPreferencesKey("cookie_jar_json")

    override suspend fun save(serializedCookies: String) {
        context.cookieDataStore.edit { prefs -> prefs[key] = serializedCookies }
    }

    override suspend fun load(): String? =
        context.cookieDataStore.data.firstOrNull()?.get(key)

    override suspend fun clear() {
        context.cookieDataStore.edit { prefs -> prefs.remove(key) }
    }
}
