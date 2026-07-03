package com.hermex.android.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hermex.android.core.network.HermexJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.customHeadersDataStore by preferencesDataStore(name = "hermex_custom_headers")

/**
 * TODO/note: header values may be sensitive (e.g. an `Authorization` or `X-Api-Key` value for a
 * reverse proxy). iOS stores these in the Keychain; Android currently uses plain DataStore here
 * to match this app's existing storage architecture (see [CookieStore]/[ServerStore], neither of
 * which is encrypted either, per this app's established "plain DataStore, not
 * EncryptedSharedPreferences" convention) -- consider moving to encrypted storage later if a
 * specific deployment needs it.
 *
 * Headers are scoped by [serverId] -- a reverse-proxy auth header for one self-hosted server has
 * no business being sent to another, so switching (or holding several configured) servers must
 * never mix them. Pre-multi-server installs had a single headers blob under an unscoped key; the
 * first [load]/[snapshot] for a given [serverId] migrates that blob in (see
 * [migrateLegacyIfNeeded]) -- in practice only the one server created by [DataStoreServerStore]'s
 * legacy-URL migration will ever find it, for the same reason described on [DataStoreCookieStore].
 *
 * Never logs the raw stored string or an individual header's name/value -- both may be secrets.
 */
class DataStoreCustomHeadersStore(private val context: Context, private val serverId: String) : CustomHeadersStore {
    private val legacyKey = stringPreferencesKey("headers_json")
    private val key = stringPreferencesKey("headers_json_$serverId")

    private val _headers = MutableStateFlow<List<CustomHttpHeader>>(emptyList())
    override val headers: StateFlow<List<CustomHttpHeader>> = _headers.asStateFlow()

    @Volatile
    private var loaded = false

    // Mirrors PersistentCookieJar.ensureLoaded(): the OkHttp interceptor needs the header list
    // synchronously on every request, but DataStore reads are suspend-only, so the very first
    // access blocks once (only on the first request after process start, or right after
    // switching to a server whose headers haven't been read yet) to hydrate the in-memory cache;
    // every call after that is a plain in-memory read.
    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runBlocking(Dispatchers.IO) { migrateLegacyIfNeeded() }
            _headers.value = decode(runBlocking(Dispatchers.IO) { readRaw() })
            loaded = true
        }
    }

    override fun snapshot(): List<CustomHttpHeader> {
        ensureLoaded()
        return _headers.value
    }

    override suspend fun load(): List<CustomHttpHeader> {
        migrateLegacyIfNeeded()
        val decoded = decode(readRaw())
        _headers.value = decoded
        loaded = true
        return decoded
    }

    override suspend fun save(headers: List<CustomHttpHeader>) {
        val sanitized = headers.sanitizedForStorage()
        // Encode before entering the edit{} lambda -- DataStore may invoke it more than once on
        // internal retry, so it should stay a pure "write this already-computed value" step
        // rather than doing serialization work itself.
        val encoded = if (sanitized.isEmpty()) null else HermexJson.encodeToString(sanitized)
        context.customHeadersDataStore.edit { prefs ->
            if (encoded == null) {
                prefs.remove(key)
            } else {
                prefs[key] = encoded
            }
        }
        _headers.value = sanitized
        loaded = true
    }

    /** Copies the legacy (unscoped) headers blob into this server's scoped key the first time
     * it's asked for, then removes the legacy key so it only ever runs once. No-op if there's
     * nothing to migrate, or if this server's scoped key is already populated. */
    private suspend fun migrateLegacyIfNeeded() {
        context.customHeadersDataStore.edit { prefs ->
            if (prefs.contains(key)) return@edit
            val legacy = prefs[legacyKey] ?: return@edit
            prefs[key] = legacy
            prefs.remove(legacyKey)
        }
    }

    private suspend fun readRaw(): String? = context.customHeadersDataStore.data.firstOrNull()?.get(key)

    private fun decode(json: String?): List<CustomHttpHeader> {
        if (json.isNullOrBlank()) return emptyList()
        // Decode failures (garbage, schema drift) yield an empty list rather than crashing --
        // never log the raw string, since it may contain a real header value.
        return runCatching { HermexJson.decodeFromString<List<CustomHttpHeader>>(json) }.getOrDefault(emptyList())
    }
}
