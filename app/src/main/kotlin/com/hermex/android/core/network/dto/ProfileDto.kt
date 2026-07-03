package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProfilesResponse(
    val profiles: List<ProfileSummary>? = null,
    val active: String? = null,
) {
    /** Which profile should read as "current" -- mirrors iOS's `effectiveDefaultProfileName`
     * precedence: the server's echoed `active` name, then a profile flagged `is_active`, then
     * one flagged `is_default`, then just the first profile in the list. */
    val effectiveDefaultProfileName: String?
        get() = active?.normalizedOrNull()
            ?: profiles?.firstOrNull { it.isActive == true }?.normalizedName
            ?: profiles?.firstOrNull { it.isDefault == true }?.normalizedName
            ?: profiles?.firstNotNullOfOrNull { it.normalizedName }
}

@Serializable
data class ProfileSummary(
    val name: String? = null,
    val path: String? = null,
    val isDefault: Boolean? = null,
    val isActive: Boolean? = null,
    val gatewayRunning: Boolean? = null,
    val model: String? = null,
    val provider: String? = null,
    val hasEnv: Boolean? = null,
    val skillCount: Int? = null,
) {
    val normalizedName: String?
        get() = name?.normalizedOrNull()

    val displayName: String
        get() = normalizedName?.let { if (it == "default") "Default" else it } ?: "Profile"

    /** "gpt-4 · OpenAI - 5 skills", matching iOS's picker subtitle -- empty when none of these
     * three fields are present rather than showing a lone " - " separator. */
    val subtitle: String?
        get() {
            val modelAndProvider = listOfNotNull(model?.normalizedOrNull(), provider?.normalizedOrNull())
                .joinToString(" · ")
            val skillsText = skillCount?.let { "$it skill${if (it == 1) "" else "s"}" }
            return listOfNotNull(modelAndProvider.ifBlank { null }, skillsText).joinToString(" - ").ifBlank { null }
        }
}

@Serializable
data class ProfileSwitchRequest(
    val name: String,
)

@Serializable
data class ProfileSwitchResponse(
    val profiles: List<ProfileSummary>? = null,
    val active: String? = null,
    val defaultModel: String? = null,
    val defaultWorkspace: String? = null,
    val error: String? = null,
)

private fun String.normalizedOrNull(): String? = trim().takeIf { it.isNotEmpty() }
