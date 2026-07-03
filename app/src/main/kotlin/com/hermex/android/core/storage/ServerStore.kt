package com.hermex.android.core.storage

/** Persists the active server's canonical URL across process death. See [CookieStore] for why
 * this is plain DataStore rather than EncryptedSharedPreferences. */
interface ServerStore {
    suspend fun save(serverUrl: String)
    suspend fun load(): String?
    suspend fun clear()
}
