package com.hermex.android.tasks

import com.hermex.android.core.network.dto.CronJob
import com.hermex.android.core.network.dto.CronOutputItem

data class TasksUiState(
    val isLoading: Boolean = true,
    val jobs: List<CronJob> = emptyList(),
    val errorMessage: String? = null,
)

data class TaskDetailUiState(
    val isLoading: Boolean = true,
    val job: CronJob? = null,
    val isRunning: Boolean? = null,
    val elapsedSeconds: Double? = null,
    val outputs: List<CronOutputItem> = emptyList(),
    val errorMessage: String? = null,
    /** True while a run/pause/resume/delete action is in flight -- distinct from [isLoading] so
     * a quick action doesn't flash the whole screen back to a full-screen spinner. */
    val isMutating: Boolean = false,
)
