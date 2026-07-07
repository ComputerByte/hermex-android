package com.hermex.android.sessions

import com.hermex.android.core.network.dto.SessionSummary
import com.hermex.android.core.storage.HeaderLogoColor

data class SessionListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sessions: List<SessionSummary> = emptyList(),
    val isCreatingSession: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val showSubagentSessions: Boolean = true,
    val headerLogoColor: HeaderLogoColor = HeaderLogoColor.DEFAULT,
    val userInitials: String = "BD",
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
            val filtered = if (showSubagentSessions) sessions
                else sessions.filter { it.sourceTag != "subagent" }
            if (query.isEmpty()) return filtered
            return filtered.filter { it.title?.contains(query, ignoreCase = true) == true }
        }

    data class SessionGroup(
        val parent: SessionSummary,
        val children: List<SessionSummary>,
    )

    /** Groups sessions by parent_session_id. Children appear nested under their parent. */
    val groupedSessions: List<SessionGroup>
        get() {
            val filtered = filteredSessions
            val childrenByParent = filtered.filter { it.parentSessionId != null }.groupBy { it.parentSessionId }
            return filtered.filter { it.parentSessionId == null }.map { parent ->
                SessionGroup(
                    parent = parent,
                    children = childrenByParent[parent.sessionId].orEmpty(),
                )
            }
        }
}
