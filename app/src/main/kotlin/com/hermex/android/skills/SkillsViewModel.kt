package com.hermex.android.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SkillsViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SkillsUiState())
    val uiState: StateFlow<SkillsUiState> = _uiState.asStateFlow()

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
                val response = safeApiCall { api.skills() }
                _uiState.update { it.copy(isLoading = false, skills = response.skills.orEmpty()) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Could not load skills.") }
            }
        }
    }
}

class SkillDetailViewModel(
    private val skillName: String,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SkillDetailUiState())
    val uiState: StateFlow<SkillDetailUiState> = _uiState.asStateFlow()

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
                val response = safeApiCall { api.skillContent(name = skillName) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = response.name ?: skillName,
                        content = response.content,
                        linkedFiles = response.linkedFiles.orEmpty(),
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Could not load skill.") }
            }
        }
    }
}
