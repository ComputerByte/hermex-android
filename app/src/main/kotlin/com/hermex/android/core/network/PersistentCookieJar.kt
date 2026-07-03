package com.hermex.android.core.network

import com.hermex.android.core.storage.CookieStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class SerializableCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
)

/**
 * OkHttp has no persistent `CookieJar` built in (its default is in-memory only). The server's
 * login cookie is HTTP-only and must survive process death, so this jar is the Android analogue
 * of iOS's `HTTPCookieStorage.shared`: an in-memory cache for fast reads on every request,
 * persisted to [CookieStore] (DataStore) on every write, and lazily rehydrated on first use.
 *
 * Shared by both the REST and SSE `OkHttpClient`s (see [NetworkModule]) -- the SSE stream
 * endpoint is itself authenticated and must carry the same session cookie as REST calls.
 */
class PersistentCookieJar(
    private val cookieStore: CookieStore,
    private val json: Json = HermexJson,
) : CookieJar {
    private val memoryCache = ConcurrentHashMap<String, Cookie>()
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loaded = false

    // Cookies are read/written from OkHttp's network dispatcher threads. The one-time load is a
    // blocking DataStore read; acceptable because it only happens once, on the very first
    // request after process start, and every subsequent call hits the in-memory cache.
    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val stored = runBlocking(Dispatchers.IO) { cookieStore.load() }
            if (stored != null) {
                runCatching { json.decodeFromString<List<SerializableCookie>>(stored) }
                    .getOrNull()
                    ?.forEach { memoryCache[cacheKey(it.name, it.domain, it.path)] = it.toCookie() }
            }
            loaded = true
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        ensureLoaded()
        val now = System.currentTimeMillis()
        cookies.forEach { cookie ->
            val key = cacheKey(cookie.name, cookie.domain, cookie.path)
            if (cookie.expiresAt <= now) memoryCache.remove(key) else memoryCache[key] = cookie
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        ensureLoaded()
        val now = System.currentTimeMillis()
        return memoryCache.values.filter { it.matches(url) && it.expiresAt > now }
    }

    /** Drops every cookie -- used on sign-out / session-expiry. */
    fun clear() {
        ensureLoaded()
        memoryCache.clear()
        persistScope.launch { cookieStore.clear() }
    }

    private fun persist() {
        val snapshot = memoryCache.values.map { it.toSerializable() }
        val encoded = json.encodeToString(snapshot)
        persistScope.launch { cookieStore.save(encoded) }
    }

    private fun cacheKey(name: String, domain: String, path: String) = "$name@$domain$path"

    private fun Cookie.toSerializable() = SerializableCookie(
        name = name,
        value = value,
        domain = domain,
        path = path,
        expiresAt = expiresAt,
        secure = secure,
        httpOnly = httpOnly,
    )

    // Note: OkHttp's public Cookie.Builder only supports host-scoped domain matching (no way to
    // reconstruct a non-host-only "matches subdomains too" cookie from the Builder API). That's
    // correct for a single self-hosted server, which is the only case this app targets.
    private fun SerializableCookie.toCookie(): Cookie =
        Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path(path)
            .expiresAt(expiresAt)
            .apply { if (secure) secure() }
            .apply { if (httpOnly) httpOnly() }
            .build()
}
