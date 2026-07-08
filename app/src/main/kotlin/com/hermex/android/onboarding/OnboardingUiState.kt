package com.hermex.android.onboarding

data class OnboardingUiState(
    val serverUrlInput: String = "",
    val passwordInput: String = "",
    val isTestingConnection: Boolean = false,
    val isLoggingIn: Boolean = false,
    /** Set once "Test connection" succeeds and the server reports auth is required. */
    val requiresPassword: Boolean = false,
    /** Set once "Test connection" has succeeded at least once for the current URL input. */
    val hasTestedConnection: Boolean = false,
    val passkeyOnlyBlocked: Boolean = false,
    val errorMessage: String? = null,
    /** True when redirected to login due to a 401 (expired session), not a fresh install. */
    val isReauthAfterExpiry: Boolean = false,
    /** Security policy classification for the current URL input. */
    val urlPolicy: com.hermex.android.auth.ServerUrlPolicy = com.hermex.android.auth.ServerUrlPolicy.Allowed,
)
