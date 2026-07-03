package com.hermex.android.sessions

import com.hermex.android.core.network.dto.SessionSummary

data class SessionListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sessions: List<SessionSummary> = emptyList(),
    val isCreatingSession: Boolean = false,
    val errorMessage: String? = null,
)
