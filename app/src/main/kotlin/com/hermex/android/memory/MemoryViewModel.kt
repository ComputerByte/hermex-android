package com.hermex.android.memory

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

/** Read-only: no write support in this MVP (the server's `POST /api/memory/write` is out of
 * scope, same as the rest of the read-only server panels -- see API_CONTRACT.md). */
class MemoryViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

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
                val response = safeApiCall { api.memory() }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        memorySection = MemorySectionUi(response.memory, response.memoryMtime),
                        userSection = MemorySectionUi(response.user, response.userMtime),
                        soulSection = MemorySectionUi(response.soul, response.soulMtime),
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Could not load memory.") }
            }
        }
    }
}
