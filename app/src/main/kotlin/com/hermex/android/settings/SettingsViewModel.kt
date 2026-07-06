package com.hermex.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.appicon.AppIconSwitcher
import com.hermex.android.core.appicon.NoOpAppIconAliasWriter
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.safeApiCall
import com.hermex.android.core.storage.AppIconVariant
import com.hermex.android.core.storage.AppearancePreferencesStore
import com.hermex.android.core.storage.ChatPreferencesStore
import com.hermex.android.core.storage.CustomHeadersStore
import com.hermex.android.core.storage.HeaderLogoColor
import com.hermex.android.core.storage.NoOpAppearancePreferencesStore
import com.hermex.android.core.storage.NoOpServerStore
import com.hermex.android.core.storage.ServerStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val chatPreferencesStore: ChatPreferencesStore,
    private val customHeadersStore: CustomHeadersStore,
    private val serverStore: ServerStore = NoOpServerStore,
    private val appearancePreferencesStore: AppearancePreferencesStore = NoOpAppearancePreferencesStore,
    private val appIconSwitcher: AppIconSwitcher = AppIconSwitcher(NoOpAppIconAliasWriter) { false },
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Called whenever the preference changes so [AppContainer] can keep its cached copy in sync. */
    var onNotificationsChanged: ((Boolean) -> Unit)? = null

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    serverUrl = authRepository.activeServerBaseUrl(),
                    activeServerName = serverStore.state.value.activeServer?.name,
                )
            }
            // Local-only, independent of server connectivity -- loaded regardless of whether the
            // rest of this call succeeds.
            _uiState.update { it.copy(expandThinkingByDefault = chatPreferencesStore.loadExpandThinkingByDefault()) }
            _uiState.update { it.copy(expandToolCallsByDefault = chatPreferencesStore.loadExpandToolCallsByDefault()) }
            _uiState.update { it.copy(notificationsEnabled = chatPreferencesStore.loadNotificationsEnabled()) }
            _uiState.update { it.copy(headerLogoColor = appearancePreferencesStore.loadHeaderLogoColor()) }
            _uiState.update { it.copy(appIconVariant = appearancePreferencesStore.loadAppIconVariant()) }
            val initials = appearancePreferencesStore.loadUserInitials()
            _uiState.update { it.copy(userInitials = initials) }
            val customHeaders = customHeadersStore.load()
            _uiState.update { it.copy(customHeaderCount = customHeaders.size) }
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

    fun setExpandToolCallsByDefault(value: Boolean) {
        _uiState.update { it.copy(expandToolCallsByDefault = value) }
        viewModelScope.launch { chatPreferencesStore.setExpandToolCallsByDefault(value) }
    }

    fun setHeaderLogoColor(color: HeaderLogoColor) {
        _uiState.update { it.copy(headerLogoColor = color) }
        viewModelScope.launch { appearancePreferencesStore.setHeaderLogoColor(color) }
    }

    /** Applies the launcher-icon change immediately (not just on next app start) -- see
     * [AppIconSwitcher]. Some launchers may take a moment to refresh the visible icon, or briefly
     * remove and re-add it, after this call. */
    fun setAppIconVariant(variant: AppIconVariant) {
        _uiState.update { it.copy(appIconVariant = variant) }
        viewModelScope.launch {
            appearancePreferencesStore.setAppIconVariant(variant)
            appIconSwitcher.applyVariant(variant)
        }
    }

    fun setUserInitials(initials: String) {
        viewModelScope.launch {
            appearancePreferencesStore.setUserInitials(initials)
            _uiState.update { it.copy(userInitials = initials) }
        }
    }

    fun setNotificationsEnabled(value: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = value) }
        viewModelScope.launch { chatPreferencesStore.setNotificationsEnabled(value) }
        onNotificationsChanged?.invoke(value)
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
