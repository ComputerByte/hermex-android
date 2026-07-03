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

/** `/api/insights` first; on failure, falls back to computing local usage analytics from
 * `/api/sessions` metadata, matching iOS's `InsightsViewModel.load()` exactly (see
 * `InsightsUiState.hasLoadedAnalytics`/`InsightsDataSource` for the resulting state machine).
 * Both endpoints are read-only GETs -- no mutation/export endpoints exist for this feature. */
class InsightsViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    /** Guards against a stale (older) load's late-arriving result clobbering a newer one -- e.g.
     * rapidly switching timeframes before the first request finishes. Mirrors iOS's
     * `activeLoadID` UUID guard checked after every await point. */
    private var loadGeneration = 0

    init { load() }

    fun selectTimeframe(timeframe: InsightsTimeframe) {
        if (timeframe == _uiState.value.timeframe) return
        _uiState.update { it.copy(timeframe = timeframe) }
        load()
    }

    fun load() {
        val generation = ++loadGeneration
        val timeframe = _uiState.value.timeframe
        val hadLoadedAnalytics = _uiState.value.hasLoadedAnalytics

        viewModelScope.launch {
            // Deliberately doesn't clear serverInsights/sessions here -- previously-loaded
            // analytics stay visible (with a refresh spinner) until this load actually produces
            // a new result, matching iOS's "keep analytics visible while timeframe refreshes".
            _uiState.update { it.copy(isLoading = true, errorMessage = null, fallbackReason = null) }

            val api = authRepository.apiForActiveServer()
            if (api == null) {
                if (generation != loadGeneration) return@launch
                _uiState.update { it.copy(isLoading = false, errorMessage = "Not signed in.") }
                return@launch
            }

            try {
                val response = safeApiCall { api.insights(days = timeframe.days) }
                if (generation != loadGeneration) return@launch
                _uiState.update {
                    it.copy(
                        serverInsights = response,
                        sessions = emptyList(),
                        loadedTimeframe = timeframe,
                        dataSource = InsightsDataSource.SERVER,
                    )
                }
            } catch (e: ApiError) {
                if (generation != loadGeneration) return@launch
                val insightsError = e.message ?: "Server insights unavailable."
                _uiState.update { it.copy(fallbackReason = insightsError) }

                try {
                    val sessionsResponse = safeApiCall { api.sessions() }
                    if (generation != loadGeneration) return@launch
                    _uiState.update {
                        it.copy(
                            serverInsights = null,
                            sessions = sessionsResponse.sessions.orEmpty(),
                            loadedTimeframe = timeframe,
                            dataSource = InsightsDataSource.LOCAL_FALLBACK,
                        )
                    }
                } catch (e2: ApiError) {
                    if (generation != loadGeneration) return@launch
                    val sessionsError = e2.message ?: "Could not load sessions."
                    _uiState.update {
                        if (hadLoadedAnalytics) {
                            it.copy(fallbackReason = sessionsError)
                        } else {
                            it.copy(errorMessage = sessionsError, dataSource = InsightsDataSource.LOCAL)
                        }
                    }
                }
            }

            if (generation == loadGeneration) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
