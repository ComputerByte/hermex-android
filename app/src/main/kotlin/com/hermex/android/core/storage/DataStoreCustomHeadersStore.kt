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
 * Never logs the raw stored string or an individual header's name/value -- both may be secrets.
 */
class DataStoreCustomHeadersStore(private val context: Context) : CustomHeadersStore {
    private val key = stringPreferencesKey("headers_json")

    private val _headers = MutableStateFlow<List<CustomHttpHeader>>(emptyList())
    override val headers: StateFlow<List<CustomHttpHeader>> = _headers.asStateFlow()

    @Volatile
    private var loaded = false

    // Mirrors PersistentCookieJar.ensureLoaded(): the OkHttp interceptor needs the header list
    // synchronously on every request, but DataStore reads are suspend-only, so the very first
    // access blocks once (only on the first request after process start) to hydrate the
    // in-memory cache; every call after that is a plain in-memory read.
    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _headers.value = decode(runBlocking(Dispatchers.IO) { readRaw() })
            loaded = true
        }
    }

    override fun snapshot(): List<CustomHttpHeader> {
        ensureLoaded()
        return _headers.value
    }

    override suspend fun load(): List<CustomHttpHeader> {
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

    private suspend fun readRaw(): String? = context.customHeadersDataStore.data.firstOrNull()?.get(key)

    private fun decode(json: String?): List<CustomHttpHeader> {
        if (json.isNullOrBlank()) return emptyList()
        // Decode failures (garbage, schema drift) yield an empty list rather than crashing --
        // never log the raw string, since it may contain a real header value.
        return runCatching { HermexJson.decodeFromString<List<CustomHttpHeader>>(json) }.getOrDefault(emptyList())
    }
}
