package com.hermex.android.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.auth.AuthState
import com.hermex.android.auth.AuthState.LoggedOut
import com.hermex.android.auth.InvalidServerUrlException
import com.hermex.android.auth.LoginOutcome
import com.hermex.android.auth.PASSKEY_ONLY_MESSAGE
import com.hermex.android.auth.ServerUrlClassifier
import com.hermex.android.auth.ServerUrlNormalizer
import com.hermex.android.auth.ServerUrlPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    // A session-expiry (401) demotes AuthState to LoggedOut(serverUrl) specifically so re-login
    // is a one-field affair -- but that's only useful if the Onboarding screen actually surfaces
    // it. Pre-fill from the repository's current state rather than always starting blank.
    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            serverUrlInput = (authRepository.state.value as? AuthState.LoggedOut)?.serverUrl.orEmpty(),
            isReauthAfterExpiry = authRepository.state.value is AuthState.LoggedOut,
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var urlValidationJob: kotlinx.coroutines.Job? = null

    fun onServerUrlChanged(value: String) {
        val policy = ServerUrlClassifier.classify(value)
        _uiState.update {
            it.copy(
                serverUrlInput = value,
                hasTestedConnection = false,
                requiresPassword = false,
                passkeyOnlyBlocked = false,
                urlPolicy = policy,
                errorMessage = when (policy) {
                    is ServerUrlPolicy.PublicHttp -> "Public HTTP servers are not allowed. Use HTTPS, or connect to a local/private self-hosted address."
                    else -> null
                },
            )
        }
        urlValidationJob?.cancel()
        urlValidationJob = viewModelScope.launch {
            if (value.isBlank()) return@launch
            delay(500L)
            try {
                ServerUrlNormalizer.normalize(value)
            } catch (e: InvalidServerUrlException) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Enter a valid server URL.") }
            }
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(passwordInput = value, errorMessage = null) }
    }

    fun testConnection() {
        val current = _uiState.value
        // Block public HTTP
        if (current.urlPolicy is ServerUrlPolicy.PublicHttp) {
            _uiState.update { it.copy(errorMessage = "Public HTTP servers are not allowed. Use HTTPS, or connect to a local/private self-hosted address.") }
            return
        }
        val serverUrl = current.serverUrlInput
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, errorMessage = null, passkeyOnlyBlocked = false) }
            try {
                val status = authRepository.testConnection(serverUrl)
                val isPasskeyOnly = status.authEnabled == true && status.passwordAuthEnabled == false
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        hasTestedConnection = true,
                        requiresPassword = status.authEnabled == true && !isPasskeyOnly,
                        passkeyOnlyBlocked = isPasskeyOnly,
                        errorMessage = if (isPasskeyOnly) PASSKEY_ONLY_MESSAGE else null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        hasTestedConnection = false,
                        errorMessage = e.message ?: "Could not reach server.",
                    )
                }
            }
        }
    }

    fun login() {
        val current = _uiState.value
        // Block public HTTP
        if (current.urlPolicy is ServerUrlPolicy.PublicHttp) {
            _uiState.update { it.copy(errorMessage = "Public HTTP servers are not allowed. Use HTTPS, or connect to a local/private self-hosted address.") }
            return
        }
        if (current.requiresPassword && current.passwordInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter the server password.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, errorMessage = null) }
            when (val outcome = authRepository.login(current.serverUrlInput, current.passwordInput)) {
                // Navigation to the session list happens by observing AuthState at the
                // nav-graph level (see HermexNavGraph), not from here.
                is LoginOutcome.Success -> _uiState.update { it.copy(isLoggingIn = false) }
                is LoginOutcome.PasskeyOnly -> _uiState.update {
                    it.copy(isLoggingIn = false, passkeyOnlyBlocked = true, errorMessage = PASSKEY_ONLY_MESSAGE)
                }
                is LoginOutcome.Failed -> _uiState.update {
                    it.copy(isLoggingIn = false, errorMessage = outcome.message)
                }
            }
        }
    }
}
