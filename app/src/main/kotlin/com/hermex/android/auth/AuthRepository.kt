package com.hermex.android.auth

import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.HermexApi
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.dto.AuthStatusResponse
import com.hermex.android.core.network.dto.LoginRequest
import com.hermex.android.core.network.safeApiCall
import com.hermex.android.core.storage.CookieStore
import com.hermex.android.core.storage.CustomHeadersStore
import com.hermex.android.core.storage.HermexServerConfig
import com.hermex.android.core.storage.InMemoryCookieStore
import com.hermex.android.core.storage.NoOpCustomHeadersStore
import com.hermex.android.core.storage.ServerStore
import com.hermex.android.core.storage.defaultServerName
import com.hermex.android.core.util.HermexLog
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** Shown when a server has auth on but explicitly reports password auth off, i.e. it signs in
 * with passkeys, which this client can't do. Mirrors iOS's `AuthManager.passkeyOnlyMessage`. */
const val PASSKEY_ONLY_MESSAGE = "This server signs in with passkeys, which Hermex doesn't support yet."

/**
 * Owns auth state for the whole app (spans Onboarding -> SessionList -> Chat), mirroring iOS's
 * `AuthManager`. A singleton owned by [com.hermex.android.AppContainer], not a ViewModel,
 * because auth state must survive navigation between screens.
 *
 * Multi-server: [networkModule] is a single long-lived instance for the app's entire lifetime --
 * it's never rebuilt when the active server changes. Instead, [useServer] repoints its cookie
 * jar and custom-headers snapshot at the newly-active server's own scoped stores (see
 * [NetworkModule.useServer]), which avoids ever leaving Retrofit/OkHttp pointed at a stale
 * server while also guaranteeing cookies/headers never leak between servers. [cookieStoreFactory]
 * / [customHeadersStoreFactory] build a server's scoped store given its id; results are memoized
 * here so repeated switches back to a previously-seen server reuse the same instance.
 */
class AuthRepository(
    private val networkModule: NetworkModule,
    private val serverStore: ServerStore,
    private val cookieStoreFactory: (serverId: String) -> CookieStore = { InMemoryCookieStore() },
    private val customHeadersStoreFactory: (serverId: String) -> CustomHeadersStore = { NoOpCustomHeadersStore },
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Unconfigured)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** True once [restoreSavedServer] has run once at startup. The UI should show a brief
     * loading state until this flips, rather than routing to Onboarding for an instant before
     * a saved server is found (there is no synchronous way to read DataStore at graph-build
     * time). */
    private val _isRestoring = MutableStateFlow(true)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private var cachedApi: Pair<String, HermexApi>? = null
    private val cookieStoreCache = mutableMapOf<String, CookieStore>()
    private val customHeadersStoreCache = mutableMapOf<String, CustomHeadersStore>()

    /** The API bound to the active server, or null when not logged in. */
    fun apiForActiveServer(): HermexApi? {
        val serverUrl = activeServerBaseUrl() ?: return null
        return apiFor(serverUrl)
    }

    /** The active server's canonical base URL, or null when not logged in. Used to build the
     * chat SSE URL, which bypasses Retrofit (see [com.hermex.android.core.network.chatStreamUrl]). */
    fun activeServerBaseUrl(): String? = (_state.value as? AuthState.LoggedIn)?.serverUrl

    /** The active server's own scoped custom-headers store, or null when not logged in -- used
     * by the Settings/Custom-Headers screens to edit headers for whichever server is active. */
    fun customHeadersStoreForActiveServer(): CustomHeadersStore? =
        (_state.value as? AuthState.LoggedIn)?.let { customHeadersStoreFor(it.serverId) }

    private fun apiFor(serverUrl: String): HermexApi {
        cachedApi?.let { (url, api) -> if (url == serverUrl) return api }
        val api = networkModule.createApi(serverUrl)
        cachedApi = serverUrl to api
        return api
    }

    private fun cookieStoreFor(id: String): CookieStore = cookieStoreCache.getOrPut(id) { cookieStoreFactory(id) }
    private fun customHeadersStoreFor(id: String): CustomHeadersStore =
        customHeadersStoreCache.getOrPut(id) { customHeadersStoreFactory(id) }

    /** Repoints [networkModule] at [config]'s own scoped cookie jar / custom headers -- must
     * happen before any request is made against [config], so a session cookie or reverse-proxy
     * header never lands in, or leaks from, a different server's storage. */
    private fun useServer(config: HermexServerConfig) {
        networkModule.useServer(cookieStoreFor(config.id), customHeadersStoreFor(config.id))
        cachedApi = null // the previously-cached Retrofit instance was bound to the old scope
    }

    /** Reads the active server (if any) at app startup and optimistically sets LoggedIn without
     * a round trip -- if the cookie has actually expired, the first authenticated request's 401
     * demotes to LoggedOut via [handleUnauthorized], exactly like a fresh relaunch on iOS. */
    suspend fun restoreSavedServer() {
        val active = serverStore.load().activeServer
        _state.value = if (active != null) {
            useServer(active)
            AuthState.LoggedIn(active.id, active.baseUrl)
        } else {
            AuthState.Unconfigured
        }
        _isRestoring.value = false
        HermexLog.d("Auth", "restoreSavedServer -> ${_state.value::class.simpleName}")
    }

    /** "Test connection" -- probes a candidate server URL without persisting anything, changing
     * [state], or touching networking scope (health/authStatus are cookie-agnostic reads). */
    suspend fun testConnection(serverUrlString: String): AuthStatusResponse {
        val normalizedUrl = ServerUrlNormalizer.normalize(serverUrlString)
        val api = networkModule.createApi(normalizedUrl)
        val health = safeApiCall { api.health() }
        check(health.status == "ok") { "Unexpected response from server." }
        return safeApiCall { api.authStatus() }
    }

    /**
     * Logs in to [serverUrlString]. If it matches an already-configured server (by normalized
     * URL), reuses that server's id -- e.g. re-authenticating after a 401, or adding a server
     * from Settings and then switching to it. Otherwise this is a brand-new server: on success
     * it's added to [ServerStore] and made active. Mirrors iOS's `AuthManager.configure`.
     *
     * Networking is repointed at the *candidate* server's scope before the login call itself (so
     * its session cookie is written straight into the right place), and rolled back to whatever
     * was active before if login fails -- this never persists anything or changes [state] on
     * failure, and never lets a candidate's cookie leak into a different server's store.
     */
    suspend fun login(serverUrlString: String, password: String): LoginOutcome {
        val normalizedUrl = try {
            ServerUrlNormalizer.normalize(serverUrlString)
        } catch (e: InvalidServerUrlException) {
            return LoginOutcome.Failed(e.message ?: "Enter a valid server URL.")
        }

        val storeState = serverStore.load()
        val existing = storeState.servers.find { sameNormalizedUrl(it.baseUrl, normalizedUrl) }
        val previousActive = storeState.activeServer

        val candidate = existing ?: HermexServerConfig(
            id = UUID.randomUUID().toString(),
            name = defaultServerName(normalizedUrl),
            baseUrl = normalizedUrl,
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )
        useServer(candidate)

        suspend fun rollBack() {
            networkModule.cookieJar.clear() // drop any partial cookie the failed attempt may have set
            if (previousActive != null) useServer(previousActive) else cachedApi = null
        }

        val api = networkModule.createApi(normalizedUrl)

        val authStatus = try {
            val health = safeApiCall { api.health() }
            if (health.status != "ok") {
                rollBack()
                return LoginOutcome.Failed("Unexpected response from server.")
            }
            safeApiCall { api.authStatus() }
        } catch (e: ApiError) {
            rollBack()
            return LoginOutcome.Failed(e.message ?: "Could not reach server.")
        }

        // Only an explicit false counts -- a missing field means an older server that doesn't
        // report it, so we must fall through to the password path (see PROJECT_SPEC #255).
        if (authStatus.authEnabled == true && authStatus.passwordAuthEnabled == false) {
            rollBack()
            return LoginOutcome.PasskeyOnly
        }

        if (authStatus.authEnabled == true) {
            if (password.isBlank()) {
                rollBack()
                return LoginOutcome.Failed("Enter the server password.")
            }
            val loginResponse = try {
                safeApiCall { api.login(LoginRequest(password)) }
            } catch (e: ApiError.Unauthorized) {
                rollBack()
                return LoginOutcome.Failed("Incorrect password.")
            } catch (e: ApiError) {
                rollBack()
                return LoginOutcome.Failed(e.message ?: "Could not sign in.")
            }
            if (loginResponse.ok != true) {
                rollBack()
                return LoginOutcome.Failed(loginResponse.error ?: "Incorrect password.")
            }
        }

        // Persist only on success: the server that actually reached a working login. Passes
        // candidate.id through explicitly -- cookies/headers were already scoped to that exact
        // id above via useServer(candidate), so the persisted config's id must match it, not a
        // new id ServerStore would otherwise generate on its own.
        val config = if (existing != null) {
            serverStore.updateServer(existing.id, existing.name, normalizedUrl)
        } else {
            serverStore.addServer(candidate.name, normalizedUrl, candidate.id)
        }
        serverStore.setActiveServer(config.id)
        _state.value = AuthState.LoggedIn(config.id, config.baseUrl)
        HermexLog.d("Auth", "login succeeded -> LoggedIn")
        return LoginOutcome.Success(config.baseUrl)
    }

    private fun sameNormalizedUrl(a: String, b: String): Boolean =
        runCatching { ServerUrlNormalizer.normalize(a) == b }.getOrDefault(false)

    /** Best-effort server-side logout (bounded so an unreachable server never blocks local
     * sign-out), then clears the active server's cookies and demotes to [AuthState.LoggedOut] --
     * the server stays configured (still shows in the server list) so switching back to it is a
     * one-field re-login, mirroring how [handleUnauthorized] already treats a stale session.
     * Use [forgetServer] to actually remove a server and its stored auth state entirely. */
    suspend fun signOut() {
        val current = _state.value as? AuthState.LoggedIn
        if (current != null) {
            val api = apiFor(current.serverUrl)
            withTimeoutOrNull(5.seconds) {
                runCatching { safeApiCall { api.logout() } }
            }
            networkModule.cookieJar.clear()
            cachedApi = null
            _state.value = AuthState.LoggedOut(current.serverId, current.serverUrl)
        }
    }

    /** Called by [NetworkModule]'s 401 interceptor when an authenticated (non-login) call gets
     * a 401: the session cookie is stale. Keeps the server identity so re-login is a one-field
     * affair, mirroring iOS's `AuthManager.handleAPIError`. Synchronous and thread-safe -- the
     * interceptor invokes it directly from an OkHttp dispatcher thread. */
    fun handleUnauthorized() {
        val (id, url) = when (val current = _state.value) {
            is AuthState.LoggedIn -> current.serverId to current.serverUrl
            is AuthState.LoggedOut -> current.serverId to current.serverUrl
            AuthState.Unconfigured -> return
        }
        networkModule.cookieJar.clear()
        _state.value = AuthState.LoggedOut(id, url)
        HermexLog.w("Auth", "handleUnauthorized -> LoggedOut (server kept)")
    }

    /** Switches the active server: repoints networking at its scope and optimistically sets
     * [state] to LoggedIn, exactly like [restoreSavedServer] does at startup -- a stale/missing
     * cookie is caught by the next request's 401 via [handleUnauthorized], not synchronously
     * here. No-ops if [serverId] doesn't match a configured server. */
    suspend fun switchActiveServer(serverId: String) {
        serverStore.setActiveServer(serverId)
        val active = serverStore.state.value.activeServer ?: return
        useServer(active)
        _state.value = AuthState.LoggedIn(active.id, active.baseUrl)
    }

    /** Removes a server entirely: its cookies and custom headers are deleted (not just cleared
     * for the current process), and it's dropped from [ServerStore]. If it was the active
     * server, another configured server (if any) becomes active; otherwise auth state falls back
     * to [AuthState.Unconfigured], matching a no-servers-configured fresh install. */
    suspend fun forgetServer(serverId: String) {
        cookieStoreFor(serverId).clear()
        customHeadersStoreFor(serverId).save(emptyList())
        cookieStoreCache.remove(serverId)
        customHeadersStoreCache.remove(serverId)

        val wasActive = serverStore.state.value.activeServerId == serverId
        serverStore.removeServer(serverId)
        if (!wasActive) return

        val newActive = serverStore.state.value.activeServer
        if (newActive != null) {
            useServer(newActive)
            _state.value = AuthState.LoggedIn(newActive.id, newActive.baseUrl)
        } else {
            networkModule.cookieJar.clear()
            cachedApi = null
            _state.value = AuthState.Unconfigured
        }
    }
}
