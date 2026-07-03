package com.hermex.android.core.network

import com.hermex.android.core.storage.CookieStore

/** Shared in-memory [CookieStore] test double -- avoids needing a real Android Context/DataStore. */
internal class FakeCookieStore : CookieStore {
    var stored: String? = null
    override suspend fun save(serializedCookies: String) { stored = serializedCookies }
    override suspend fun load(): String? = stored
    override suspend fun clear() { stored = null }
}
