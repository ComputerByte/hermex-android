package com.hermex.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.safeApiCall
import com.hermex.android.core.storage.ChatPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val chatPreferencesStore: ChatPreferencesStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, serverUrl = authRepository.activeServerBaseUrl())
            }
            // Local-only, independent of server connectivity -- loaded regardless of whether the
            // rest of this call succeeds.
            _uiState.update { it.copy(expandThinkingByDefault = chatPreferencesStore.loadExpandThinkingByDefault()) }
            val api = authRepository.apiForActiveServer()
            if (api == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Not signed in.") }
                return@launch
            }
            try {
                val response = safeApiCall { api.serverSettings() }
                _uiState.update { it.copy(serverVersion = response.displayVersion) }
            } catch (e: ApiError) {
                // Best-effort: the server URL still shows even if the version lookup fails.
                _uiState.update { it.copy(errorMessage = e.message ?: "Could not load server settings.") }
            }
            try {
                val response = safeApiCall { api.models() }
                _uiState.update { it.copy(defaultModel = response.defaultModel) }
            } catch (e: ApiError) {
                // Best-effort, same as above -- the rest of the screen still shows.
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun setExpandThinkingByDefault(value: Boolean) {
        _uiState.update { it.copy(expandThinkingByDefault = value) }
        viewModelScope.launch { chatPreferencesStore.setExpandThinkingByDefault(value) }
    }

    /** Doesn't need to navigate or flip [SettingsUiState.isSigningOut] back -- once
     * [AuthRepository.state] flips to `Unconfigured`, `HermexNavGraph` routes back to Onboarding
     * and unmounts this screen. */
    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningOut = true) }
            authRepository.signOut()
        }
    }
}
