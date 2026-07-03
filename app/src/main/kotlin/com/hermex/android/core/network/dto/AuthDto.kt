package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthStatusResponse(
    val authEnabled: Boolean? = null,
    val loggedIn: Boolean? = null,
    val passwordAuthEnabled: Boolean? = null,
    val passkeysEnabled: Boolean? = null,
    val passwordlessEnabled: Boolean? = null,
)

@Serializable
data class LoginRequest(val password: String)

@Serializable
data class LoginResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
)

/** Serializes to `{}` -- Retrofit requires a @Body for POST; the server expects an empty object. */
@Serializable
object EmptyRequestBody
