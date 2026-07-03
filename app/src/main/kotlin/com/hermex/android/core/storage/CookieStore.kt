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

/** Default used wherever no real persisted store is wired in -- e.g.
 * [com.hermex.android.auth.AuthRepository] before any server has ever been resolved. Cookies live
 * only in process memory for this instance's lifetime; never shared across servers since each
 * gets its own instance (see [com.hermex.android.auth.AuthRepository]'s per-server-id cache). */
class InMemoryCookieStore : CookieStore {
    private var stored: String? = null
    override suspend fun save(serializedCookies: String) { stored = serializedCookies }
    override suspend fun load(): String? = stored
    override suspend fun clear() { stored = null }
}
