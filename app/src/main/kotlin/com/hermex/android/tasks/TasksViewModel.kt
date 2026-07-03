package com.hermex.android.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.HermexApi
import com.hermex.android.core.network.dto.CronJobIdRequest
import com.hermex.android.core.network.dto.CronJobStatus
import com.hermex.android.core.network.dto.CronMutationResponse
import com.hermex.android.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TasksViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val api = authRepository.apiForActiveServer()
            if (api == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Not signed in.") }
                return@launch
            }
            try {
                val response = safeApiCall { api.crons() }
                _uiState.update { it.copy(isLoading = false, jobs = response.jobs.orEmpty()) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Could not load tasks.") }
            }
        }
    }
}

/** There is no single "get one job" endpoint -- job metadata (name/schedule/prompt) only comes
 * back from the list endpoint, so this re-fetches the list and picks out [jobId], then layers
 * live running-state and recent output on top from their own endpoints. Status/output are
 * best-effort (a failure there still leaves the job's core metadata visible); a missing job
 * entirely, or the list call itself failing, is a hard error.
 *
 * Run Now / Pause / Resume / Delete are real mutations (server-side POSTs), unlike the rest of
 * this app's read-only server panels -- an explicit, deliberate scope addition beyond the
 * original MVP plan, requested after seeing the iOS app's fuller Tasks feature. */
class TaskDetailViewModel(
    private val jobId: String,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            refresh()
        }
    }

    fun runNow() = mutate { api -> safeApiCall { api.cronRun(CronJobIdRequest(jobId)) } }

    fun togglePauseResume() {
        val isPaused = _uiState.value.job?.status == CronJobStatus.PAUSED
        mutate { api ->
            if (isPaused) safeApiCall { api.cronResume(CronJobIdRequest(jobId)) }
            else safeApiCall { api.cronPause(CronJobIdRequest(jobId)) }
        }
    }

    /** [onDeleted] is only invoked on confirmed success -- the caller uses it to pop back and
     * signal the Tasks list to refresh, so it should never fire for a failed delete. */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val api = authRepository.apiForActiveServer()
            if (api == null) {
                _uiState.update { it.copy(errorMessage = "Not signed in.") }
                return@launch
            }
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            try {
                val response = safeApiCall { api.cronDelete(CronJobIdRequest(jobId)) }
                if (response.ok == false) {
                    _uiState.update { it.copy(isMutating = false, errorMessage = response.error ?: "Could not delete task.") }
                } else {
                    _uiState.update { it.copy(isMutating = false) }
                    onDeleted()
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isMutating = false, errorMessage = e.message ?: "Could not delete task.") }
            }
        }
    }

    /** Run/pause/resume all follow the same shape: fire the mutation, then reload the job's full
     * state from scratch (rather than trusting the mutation response's partial `job` snapshot)
     * so status/running/output stay consistent. Uses `isMutating`, not `isLoading`, so the
     * screen doesn't flash back to a full-screen spinner for a quick action. */
    private fun mutate(call: suspend (HermexApi) -> CronMutationResponse) {
        viewModelScope.launch {
            val api = authRepository.apiForActiveServer()
            if (api == null) {
                _uiState.update { it.copy(errorMessage = "Not signed in.") }
                return@launch
            }
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            try {
                val response = call(api)
                if (response.ok == false) {
                    _uiState.update { it.copy(isMutating = false, errorMessage = response.error ?: "Action failed.") }
                } else {
                    refresh(isMutating = true)
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isMutating = false, errorMessage = e.message ?: "Action failed.") }
            }
        }
    }

    private suspend fun refresh(isMutating: Boolean = false) {
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(isLoading = false, isMutating = false, errorMessage = "Not signed in.") }
            return
        }
        try {
            val jobsResponse = safeApiCall { api.crons() }
            val job = jobsResponse.jobs.orEmpty().firstOrNull { it.jobId == jobId }
            val status = runCatching { safeApiCall { api.cronStatus(jobId) } }.getOrNull()
            val output = runCatching { safeApiCall { api.cronOutput(jobId) } }.getOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isMutating = false,
                    job = job,
                    isRunning = status?.running,
                    elapsedSeconds = status?.elapsed,
                    outputs = output?.outputs.orEmpty(),
                    errorMessage = if (job == null) "Task not found." else null,
                )
            }
        } catch (e: ApiError) {
            _uiState.update {
                it.copy(isLoading = false, isMutating = false, errorMessage = e.message ?: "Could not load task.")
            }
        }
    }
}
