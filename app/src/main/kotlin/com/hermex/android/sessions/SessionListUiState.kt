package com.hermex.android.sessions

import com.hermex.android.core.network.dto.SessionSummary
import com.hermex.android.core.storage.HeaderLogoColor

data class SessionListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sessions: List<SessionSummary> = emptyList(),
    val isCreatingSession: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val headerLogoColor: HeaderLogoColor = HeaderLogoColor.DEFAULT,
    /** True when [sessions] came from the offline cache rather than a successful network
     * fetch -- either shown immediately on screen load (before the network result lands) or kept
     * on screen because the network call then failed. */
    val isShowingCachedData: Boolean = false,
    /** User-facing explanation for [isShowingCachedData], e.g. "Unable to reach server -- showing
     * cached sessions." Null whenever [isShowingCachedData] is false. */
    val cacheStatusMessage: String? = null,
) {
    /** Filters by title only -- [SessionSummary] carries no message content client-side to
     * search against. */
    val filteredSessions: List<SessionSummary>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return sessions
            return sessions.filter { it.title?.contains(query, ignoreCase = true) == true }
        }
}
