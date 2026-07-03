package com.hermex.android.auth

sealed interface AuthState {
    data object Unconfigured : AuthState
    data class LoggedOut(val serverId: String, val serverUrl: String) : AuthState
    data class LoggedIn(val serverId: String, val serverUrl: String) : AuthState
}

val AuthState.serverUrlOrNull: String?
    get() = when (this) {
        AuthState.Unconfigured -> null
        is AuthState.LoggedOut -> serverUrl
        is AuthState.LoggedIn -> serverUrl
    }

val AuthState.serverIdOrNull: String?
    get() = when (this) {
        AuthState.Unconfigured -> null
        is AuthState.LoggedOut -> serverId
        is AuthState.LoggedIn -> serverId
    }
