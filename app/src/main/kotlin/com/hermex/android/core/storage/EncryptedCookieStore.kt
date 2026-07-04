package com.hermex.android.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encrypted cookie store backed by [EncryptedSharedPreferences] with AES256_GCM.
 *
 * Each server's cookie jar is stored under a scoped key (`cookie_jar_json_$serverId`)
 * in a single encrypted prefs file (`hermex_cookies_encrypted`). On first [load] for a given
 * serverId the store checks for a legacy [DataStoreCookieStore] value and migrates it in,
 * then clears the legacy store so migration only runs once.
 *
 * Threading: [EncryptedSharedPreferences] is synchronous (backed by `SharedPreferences`), so all
 * public methods dispatch to [Dispatchers.IO] to keep the caller's coroutine free.
 */
class EncryptedCookieStore(
    private val context: Context,
    private val serverId: String,
    private val legacyCookieStore: CookieStore? = null,
) : CookieStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val key: String get() = "cookie_jar_json_$serverId"

    override suspend fun save(serializedCookies: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, serializedCookies).apply()
    }

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        migrateIfNeeded()
        prefs.getString(key, null)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }

    /**
     * Migrates from a legacy [DataStoreCookieStore] on first [load] for this serverId.
     * No-op if the encrypted store already has a value, or if no [legacyCookieStore] was
     * provided, or if the legacy store has no value.
     *
     * Migration is atomic: the encrypted value is written first; only after it succeeds is the
     * legacy value cleared. A crash between the two leaves both copies intact (legacy untouched),
     * so the next [load] retries migration.
     */
    private suspend fun migrateIfNeeded() {
        if (prefs.contains(key)) return
        val legacy = legacyCookieStore?.load() ?: return
        prefs.edit().putString(key, legacy).apply()
        legacyCookieStore.clear()
    }

    companion object {
        private const val PREFS_FILE_NAME = "hermex_cookies_encrypted"
    }
}
