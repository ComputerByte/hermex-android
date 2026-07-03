package com.hermex.android.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
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

/**
 * A 401 during any call here is handled globally: [AuthRepository]'s state flips to LoggedOut
 * (via NetworkModule's interceptor) and HermexNavGraph reacts by navigating back to Onboarding.
 * The [ApiError.message] set on [SessionListUiState.errorMessage] in that case is just
 * incidental -- it may flash briefly before the screen unmounts, which is fine.
 */
class SessionListViewModel(
    private val authRepository: AuthRepository,
    private val appearancePreferencesStore: AppearancePreferencesStore = NoOpAppearancePreferencesStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        load()
        loadHeaderLogoColor()
    }

    /** Re-reads just the header color preference (fast, local, no network) -- used to reflect a
     * change made on the Settings screen without re-fetching sessions, see
     * [com.hermex.android.navigation.HermexNavGraph]. */
    fun loadHeaderLogoColor() {
        viewModelScope.launch {
            _uiState.update { it.copy(headerLogoColor = appearancePreferencesStore.loadHeaderLogoColor()) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            fetchSessions { sessions, error ->
                _uiState.update { it.copy(isLoading = false, sessions = sessions ?: it.sessions, errorMessage = error) }
            }
        }
    }

    fun onSearchQueryChanged(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            fetchSessions { sessions, error ->
                _uiState.update { it.copy(isRefreshing = false, sessions = sessions ?: it.sessions, errorMessage = error) }
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
