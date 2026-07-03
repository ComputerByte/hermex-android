package com.hermex.android.projects

import com.hermex.android.core.network.dto.ProjectSummary

data class ProjectsUiState(
    val isLoading: Boolean = true,
    val projects: List<ProjectSummary> = emptyList(),
    /** True while a create/rename/delete is in flight -- distinct from [isLoading] so a quick
     * action doesn't flash the whole screen back to a full-screen spinner. */
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
)
