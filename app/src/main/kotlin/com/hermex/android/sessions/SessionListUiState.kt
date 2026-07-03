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
