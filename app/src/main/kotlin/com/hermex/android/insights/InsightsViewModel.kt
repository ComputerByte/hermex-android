package com.hermex.android.insights

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

/** Read-only: `/api/insights` is a GET with no mutation/export endpoints, matching iOS. Changing
 * the timeframe re-fetches from the server with a different `days` param rather than re-filtering
 * client-side data. */
class InsightsViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init { load() }

    fun selectTimeframe(timeframe: InsightsTimeframe) {
        if (timeframe == _uiState.value.timeframe) return
        _uiState.update { it.copy(timeframe = timeframe) }
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
                val response = safeApiCall { api.insights(days = _uiState.value.timeframe.days) }
                _uiState.update { it.copy(isLoading = false, insights = response) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Could not load insights.") }
            }
        }
    }
}
