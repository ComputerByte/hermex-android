package com.hermex.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.core.storage.CustomHeadersStore
import com.hermex.android.core.storage.CustomHttpHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Add/remove/edit rows for custom request headers -- persisting is explicit ([save]), so
 * navigating back without saving discards whatever's currently being edited. */
class CustomHeadersViewModel(
    private val customHeadersStore: CustomHeadersStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomHeadersUiState())
    val uiState: StateFlow<CustomHeadersUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val headers = customHeadersStore.load()
            _uiState.update { it.copy(isLoading = false, headers = headers) }
        }
    }

    fun addRow() {
        _uiState.update { it.copy(headers = it.headers + CustomHttpHeader()) }
    }

    fun removeRow(index: Int) {
        _uiState.update { state -> state.copy(headers = state.headers.filterIndexed { i, _ -> i != index }) }
    }

    fun updateName(index: Int, name: String) {
        _uiState.update { state ->
            state.copy(headers = state.headers.mapIndexed { i, header -> if (i == index) header.copy(name = name) else header })
        }
    }

    fun updateValue(index: Int, value: String) {
        _uiState.update { state ->
            state.copy(headers = state.headers.mapIndexed { i, header -> if (i == index) header.copy(value = value) else header })
        }
    }

    /** Toggle reveal state for a specific header (shows value vs masked). */
    fun toggleReveal(headerId: String) {
        _uiState.update { state ->
            val newRevealed = if (headerId in state.revealedHeaderIds) {
                state.revealedHeaderIds - headerId
            } else {
                state.revealedHeaderIds + headerId
            }
            state.copy(revealedHeaderIds = newRevealed)
        }
    }

    /** Persists the current draft rows (blank-name rows are dropped by the store itself) and
     * invokes [onSaved] -- the caller navigates back on success. */
    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            customHeadersStore.save(_uiState.value.headers)
            _uiState.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}
