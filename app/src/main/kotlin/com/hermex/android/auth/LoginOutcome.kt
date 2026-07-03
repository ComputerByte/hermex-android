package com.hermex.android.auth

sealed interface LoginOutcome {
    data class Success(val serverUrl: String) : LoginOutcome
    data object PasskeyOnly : LoginOutcome
    data class Failed(val message: String) : LoginOutcome
}
