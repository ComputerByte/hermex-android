package com.hermex.android.auth

import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.HermexApi
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.dto.AuthStatusResponse
import com.hermex.android.core.network.dto.LoginRequest
import com.hermex.android.core.network.safeApiCall
import com.hermex.android.core.storage.ServerStore
import com.hermex.android.core.util.HermexLog
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
 */
class AuthRepository(
    private val networkModule: NetworkModule,
    private val serverStore: ServerStore,
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

    /** The API bound to the active server, or null when not logged in. */
    fun apiForActiveServer(): HermexApi? {
        val serverUrl = activeServerBaseUrl() ?: return null
        return apiFor(serverUrl)
    }

    /** The active server's canonical base URL, or null when not logged in. Used to build the
     * chat SSE URL, which bypasses Retrofit (see [com.hermex.android.core.network.chatStreamUrl]). */
    fun activeServerBaseUrl(): String? = (_state.value as? AuthState.LoggedIn)?.serverUrl

    private fun apiFor(serverUrl: String): HermexApi {
        cachedApi?.let { (url, api) -> if (url == serverUrl) return api }
        val api = networkModule.createApi(serverUrl)
        cachedApi = serverUrl to api
        return api
    }

    /** Reads the saved server URL (if any) at app startup and optimistically sets LoggedIn
     * without a round trip -- if the cookie has actually expired, the first authenticated
     * request's 401 demotes to LoggedOut via [handleUnauthorized], exactly like a fresh
     * relaunch on iOS. */
    suspend fun restoreSavedServer() {
        val saved = serverStore.load()
        _state.value = if (saved != null) AuthState.LoggedIn(saved) else AuthState.Unconfigured
        _isRestoring.value = false
        HermexLog.d("Auth", "restoreSavedServer -> ${_state.value::class.simpleName}")
    }

    /** "Test connection" -- probes a candidate server URL without persisting anything or
     * changing [state]. Throws [InvalidServerUrlException] or an [ApiError]. */
    suspend fun testConnection(serverUrlString: String): AuthStatusResponse {
        val normalizedUrl = ServerUrlNormalizer.normalize(serverUrlString)
        val api = networkModule.createApi(normalizedUrl)
        val health = safeApiCall { api.health() }
        check(health.status == "ok") { "Unexpected response from server." }
        return safeApiCall { api.authStatus() }
    }

    /** The onboarding login flow: normalize, probe, reject passkey-only servers, log in if a
     * password is required, and persist only on success -- mirrors iOS's `AuthManager.configure`. */
    suspend fun login(serverUrlString: String, password: String): LoginOutcome {
        val normalizedUrl = try {
            ServerUrlNormalizer.normalize(serverUrlString)
        } catch (e: InvalidServerUrlException) {
            return LoginOutcome.Failed(e.message ?: "Enter a valid server URL.")
        }

        val api = networkModule.createApi(normalizedUrl)

        val authStatus = try {
            val health = safeApiCall { api.health() }
            if (health.status != "ok") return LoginOutcome.Failed("Unexpected response from server.")
            safeApiCall { api.authStatus() }
        } catch (e: ApiError) {
            return LoginOutcome.Failed(e.message ?: "Could not reach server.")
        }

        // Only an explicit false counts -- a missing field means an older server that doesn't
        // report it, so we must fall through to the password path (see PROJECT_SPEC #255).
        if (authStatus.authEnabled == true && authStatus.passwordAuthEnabled == false) {
            return LoginOutcome.PasskeyOnly
        }

        if (authStatus.authEnabled == true) {
            if (password.isBlank()) {
                return LoginOutcome.Failed("Enter the server password.")
            }
            val loginResponse = try {
                safeApiCall { api.login(LoginRequest(password)) }
            } catch (e: ApiError.Unauthorized) {
                return LoginOutcome.Failed("Incorrect password.")
            } catch (e: ApiError) {
                return LoginOutcome.Failed(e.message ?: "Could not sign in.")
            }
            if (loginResponse.ok != true) {
                return LoginOutcome.Failed(loginResponse.error ?: "Incorrect password.")
            }
        }

        // Persist only on success: the server URL that actually reached a working login.
        serverStore.save(normalizedUrl)
        cachedApi = normalizedUrl to api
        _state.value = AuthState.LoggedIn(normalizedUrl)
        HermexLog.d("Auth", "login succeeded -> LoggedIn")
        return LoginOutcome.Success(normalizedUrl)
    }

    /** Best-effort server-side logout (bounded so an unreachable server never blocks local
     * sign-out), then clears cookies and the saved server, returning to Unconfigured. */
    suspend fun signOut() {
        val serverUrl = (_state.value as? AuthState.LoggedIn)?.serverUrl
        if (serverUrl != null) {
            val api = apiFor(serverUrl)
            withTimeoutOrNull(5.seconds) {
                runCatching { safeApiCall { api.logout() } }
            }
        }
        clearLocalAuth()
    }

    /** Called by [NetworkModule]'s 401 interceptor when an authenticated (non-login) call gets
     * a 401: the session cookie is stale. Keeps the server URL so re-login is a one-field
     * affair, mirroring iOS's `AuthManager.handleAPIError`. Synchronous and thread-safe -- the
     * interceptor invokes it directly from an OkHttp dispatcher thread. */
    fun handleUnauthorized() {
        val serverUrl = when (val current = _state.value) {
            is AuthState.LoggedIn -> current.serverUrl
            is AuthState.LoggedOut -> current.serverUrl
            AuthState.Unconfigured -> return
        }
        networkModule.cookieJar.clear()
        _state.value = AuthState.LoggedOut(serverUrl)
        HermexLog.w("Auth", "handleUnauthorized -> LoggedOut (server URL kept)")
    }

    private suspend fun clearLocalAuth() {
        networkModule.cookieJar.clear()
        serverStore.clear()
        cachedApi = null
        _state.value = AuthState.Unconfigured
    }
}
