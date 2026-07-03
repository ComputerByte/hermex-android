package com.hermex.android.core.storage

/**
 * Persists the serialized cookie jar (a JSON blob) across process death. Backed by plain Jetpack
 * DataStore, not `androidx.security`/`EncryptedSharedPreferences` (deprecated) -- the app's
 * private data directory is sandboxed per-app by the OS, which is sufficient for a self-hosted
 * server's session cookie and URL (neither is a long-lived platform-wide credential).
 */
interface CookieStore {
    suspend fun save(serializedCookies: String)
    suspend fun load(): String?
    suspend fun clear()
}
