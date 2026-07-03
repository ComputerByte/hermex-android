package com.hermex.android.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.HermexApi
import com.hermex.android.core.network.dto.CreateProjectRequest
import com.hermex.android.core.network.dto.ProjectIdRequest
import com.hermex.android.core.network.dto.ProjectMutationResponse
import com.hermex.android.core.network.dto.RenameProjectRequest
import com.hermex.android.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Project CRUD only -- assigning sessions to a project ("Move to Project" on iOS) is deliberately
 * out of scope for this slice; projects are creatable/renameable/deletable here but not yet
 * usable for filtering the session list. */
class ProjectsViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            fetchProjects()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun fetchProjects() {
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(errorMessage = "Not signed in.") }
            return
        }
        try {
            val response = safeApiCall { api.projects() }
            _uiState.update { it.copy(projects = response.projects.orEmpty()) }
        } catch (e: ApiError) {
            _uiState.update { it.copy(errorMessage = e.message ?: "Could not load projects.") }
        }
    }

    fun create(name: String, color: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate { api -> safeApiCall { api.createProject(CreateProjectRequest(trimmed, color)) } }
    }

    fun rename(projectId: String, name: String, color: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate { api -> safeApiCall { api.renameProject(RenameProjectRequest(projectId, trimmed, color)) } }
    }

    fun delete(projectId: String) {
        mutate { api -> safeApiCall { api.deleteProject(ProjectIdRequest(projectId)) } }
    }

    private fun mutate(call: suspend (HermexApi) -> ProjectMutationResponse) {
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(errorMessage = "Not signed in.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null) }
            try {
                val response = call(api)
                if (response.ok == false) {
                    _uiState.update { it.copy(errorMessage = response.error ?: "Action failed.") }
                } else {
                    // Reload before clearing isMutating, so a caller waiting on "!isMutating"
                    // observes the refreshed list, not the pre-mutation one.
                    fetchProjects()
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Action failed.") }
            }
            _uiState.update { it.copy(isMutating = false) }
        }
    }
}
