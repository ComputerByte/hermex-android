package com.hermex.android.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.cache.NoOpOfflineCacheRepository
import com.hermex.android.core.cache.OfflineCacheRepository
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.dto.NewSessionRequest
import com.hermex.android.core.network.dto.SessionSummary
import com.hermex.android.core.network.safeApiCall
import com.hermex.android.core.storage.AppearancePreferencesStore
import com.hermex.android.core.storage.NoOpAppearancePreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val OFFLINE_CACHE_MESSAGE = "Unable to reach server -- showing cached sessions"

/**
 * A 401 during any call here is handled globally: [AuthRepository]'s state flips to LoggedOut
 * (via NetworkModule's interceptor) and HermexNavGraph reacts by navigating back to Onboarding.
 * The [ApiError.message] set on [SessionListUiState.errorMessage] in that case is just
 * incidental -- it may flash briefly before the screen unmounts, which is fine.
 */
class SessionListViewModel(
    private val authRepository: AuthRepository,
    private val appearancePreferencesStore: AppearancePreferencesStore = NoOpAppearancePreferencesStore,
    private val offlineCacheRepository: OfflineCacheRepository = NoOpOfflineCacheRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        load()
        loadHeaderLogoColor()
        loadUserInitials()
    }

    /** Re-reads just the header color preference (fast, local, no network) -- used to reflect a
     * change made on the Settings screen without re-fetching sessions, see
     * [com.hermex.android.navigation.HermexNavGraph]. */
    fun loadHeaderLogoColor() {
        viewModelScope.launch {
            _uiState.update { it.copy(headerLogoColor = appearancePreferencesStore.loadHeaderLogoColor()) }
        }
    }

    fun loadUserInitials() {
        viewModelScope.launch {
            _uiState.update { it.copy(userInitials = appearancePreferencesStore.loadUserInitials()) }
        }
    }

    /** Shows the offline cache immediately (if there is one) while the network fetch is still in
     * flight, so switching back to a server you've seen before doesn't start from a blank list --
     * then a successful fetch replaces it with fresh data (and refreshes the cache for next time),
     * while a failed fetch just leaves the cached sessions on screen with [SessionListUiState.cacheStatusMessage]
     * explaining why. If there's no cache at all, a failed fetch falls through to the existing
     * plain error state. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val serverId = authRepository.activeServerId()
            if (serverId != null) {
                val cached = offlineCacheRepository.cachedSessions(serverId)
                if (cached.isNotEmpty()) {
                    _uiState.update {
                        it.copy(sessions = cached, isShowingCachedData = true, cacheStatusMessage = OFFLINE_CACHE_MESSAGE)
                    }
                }
            }
            fetchSessions { sessions, error ->
                if (sessions != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessions = sessions,
                            errorMessage = null,
                            isShowingCachedData = false,
                            cacheStatusMessage = null,
                        )
                    }
                    if (serverId != null) {
                        viewModelScope.launch { offlineCacheRepository.saveSessions(serverId, sessions) }
                    }
                } else {
                    val hasCache = _uiState.value.isShowingCachedData
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (hasCache) null else error,
                            cacheStatusMessage = if (hasCache) OFFLINE_CACHE_MESSAGE else null,
                        )
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    /** Pull-to-refresh: unlike [load], never (re-)shows the cache first -- something is already on
     * screen (fresh or cached). A failure here just relabels whatever's currently displayed as
     * stale rather than re-querying the cache. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val serverId = authRepository.activeServerId()
            fetchSessions { sessions, error ->
                if (sessions != null) {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            sessions = sessions,
                            isShowingCachedData = false,
                            cacheStatusMessage = null,
                        )
                    }
                    if (serverId != null) {
                        viewModelScope.launch { offlineCacheRepository.saveSessions(serverId, sessions) }
                    }
                } else {
                    val hasAnySessions = _uiState.value.sessions.isNotEmpty()
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = if (hasAnySessions) null else error,
                            isShowingCachedData = hasAnySessions,
                            cacheStatusMessage = if (hasAnySessions) OFFLINE_CACHE_MESSAGE else null,
                        )
                    }
                }
            }
        }
    }

    private suspend fun fetchSessions(onDone: (sessions: List<SessionSummary>?, error: String?) -> Unit) {
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            onDone(null, "Not signed in.")
            return
        }
        try {
            val response = safeApiCall { api.sessions() }
            onDone(response.sessions.orEmpty(), null)
        } catch (e: ApiError) {
            onDone(null, e.message ?: "Could not load sessions.")
        }
    }

    /** Creates a new session with the server's default workspace/model and, on success, invokes
     * [onCreated] with the new session id so the caller can navigate to it. */
    fun createSession(onCreated: (String) -> Unit) {
        val api = authRepository.apiForActiveServer() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingSession = true, errorMessage = null) }
            try {
                val response = safeApiCall { api.newSession(NewSessionRequest()) }
                val sessionId = response.session?.sessionId
                if (sessionId != null) {
                    _uiState.update { it.copy(isCreatingSession = false) }
                    onCreated(sessionId)
                } else {
                    _uiState.update {
                        it.copy(isCreatingSession = false, errorMessage = "Server did not return a session id.")
                    }
                }
            } catch (e: ApiError) {
                _uiState.update {
                    it.copy(isCreatingSession = false, errorMessage = e.message ?: "Could not create session.")
                }
            }
        }
    }
}
