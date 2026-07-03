package com.hermex.android.profiles

import com.hermex.android.core.network.dto.ProfileSummary

data class ProfilesUiState(
    val isLoading: Boolean = true,
    val profiles: List<ProfileSummary> = emptyList(),
    val activeName: String? = null,
    val searchQuery: String = "",
    /** Name of the profile currently being switched to -- distinct from [isLoading] so picking
     * a row shows a small inline spinner rather than blanking the whole list. */
    val switchingTo: String? = null,
    val errorMessage: String? = null,
) {
    val filteredProfiles: List<ProfileSummary>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return profiles
            return profiles.filter { it.displayName.contains(query, ignoreCase = true) }
        }
}
