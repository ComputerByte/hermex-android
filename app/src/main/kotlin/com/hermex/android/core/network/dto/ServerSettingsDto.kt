package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

/** `GET /api/settings` -- server-wide settings, distinct from this app's own local Settings
 * screen. Named `ServerSettingsResponse` (not `SettingsResponse`, which is what iOS calls it) to
 * avoid confusion with the Android app's own `settings` package. */
@Serializable
data class ServerSettingsResponse(
    val botName: String? = null,
    val webuiVersion: String? = null,
    val version: String? = null,
    val theme: String? = null,
) {
    val displayVersion: String?
        get() = version ?: webuiVersion
}
