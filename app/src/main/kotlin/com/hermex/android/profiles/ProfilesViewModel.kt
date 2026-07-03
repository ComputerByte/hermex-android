package com.hermex.android.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.dto.ProfileSwitchRequest
import com.hermex.android.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Android equivalent of iOS's Settings -> "Default Profile" picker (`DefaultProfilePickerView`)
 * -- a standalone nav-row screen rather than a Settings-hosted sheet, since this app has no
 * Settings hub yet. Lists every server profile and lets the user switch which one is active. */
class ProfilesViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val api = authRepository.apiForActiveServer()
            if (api == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Not signed in.") }
                return@launch
            }
            try {
                val response = safeApiCall { api.profiles() }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        profiles = response.profiles.orEmpty(),
                        activeName = response.effectiveDefaultProfileName,
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Could not load profiles.") }
            }
        }
    }

    fun onSearchQueryChanged(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun switchTo(name: String) {
        if (name == _uiState.value.activeName || _uiState.value.switchingTo != null) return
        viewModelScope.launch {
            val api = authRepository.apiForActiveServer()
            if (api == null) {
                _uiState.update { it.copy(errorMessage = "Not signed in.") }
                return@launch
            }
            _uiState.update { it.copy(switchingTo = name, errorMessage = null) }
            try {
                val response = safeApiCall { api.switchProfile(ProfileSwitchRequest(name)) }
                if (response.error != null) {
                    _uiState.update { it.copy(switchingTo = null, errorMessage = response.error) }
                } else {
                    _uiState.update {
                        it.copy(
                            switchingTo = null,
                            profiles = response.profiles ?: it.profiles,
                            activeName = response.active ?: name,
                        )
                    }
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(switchingTo = null, errorMessage = e.message ?: "Could not switch profile.") }
            }
        }
    }
}
