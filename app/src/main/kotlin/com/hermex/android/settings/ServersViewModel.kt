package com.hermex.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.auth.InvalidServerUrlException
import com.hermex.android.auth.ServerUrlNormalizer
import com.hermex.android.core.storage.HermexServerConfig
import com.hermex.android.core.storage.ServerStore
import com.hermex.android.core.storage.defaultServerName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServersViewModel(
    private val authRepository: AuthRepository,
    private val serverStore: ServerStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ServersUiState())
    val uiState: StateFlow<ServersUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val state = serverStore.load()
            _uiState.update { it.copy(isLoading = false, servers = state.servers, activeServerId = state.activeServerId) }
        }
    }

    /** No-op if [serverId] is already active -- avoids an unnecessary networking repoint. */
    fun switchTo(serverId: String) {
        if (serverId == _uiState.value.activeServerId) return
        viewModelScope.launch {
            _uiState.update { it.copy(switchingServerId = serverId) }
            authRepository.switchActiveServer(serverId)
            _uiState.update { it.copy(switchingServerId = null) }
            load()
        }
    }

    fun startAdding() {
        _uiState.update {
            it.copy(showEditor = true, editingServerId = null, editorName = "", editorUrl = "", editorError = null)
        }
    }

    fun startEditing(config: HermexServerConfig) {
        _uiState.update {
            it.copy(showEditor = true, editingServerId = config.id, editorName = config.name, editorUrl = config.baseUrl, editorError = null)
        }
    }

    fun updateEditorName(value: String) {
        _uiState.update { it.copy(editorName = value) }
    }

    fun updateEditorUrl(value: String) {
        _uiState.update { it.copy(editorUrl = value, editorError = null) }
    }

    fun dismissEditor() {
        _uiState.update {
            it.copy(showEditor = false, editingServerId = null, editorName = "", editorUrl = "", editorError = null)
        }
    }

    fun saveEditor() {
        val current = _uiState.value
        val normalizedUrl = try {
            ServerUrlNormalizer.normalize(current.editorUrl)
        } catch (e: InvalidServerUrlException) {
            _uiState.update { it.copy(editorError = e.message ?: "Enter a valid server URL.") }
            return
        }

        val editingId = current.editingServerId
        val isDuplicate = current.servers.any { it.id != editingId && it.baseUrl == normalizedUrl }
        if (isDuplicate) {
            _uiState.update { it.copy(editorError = "This server is already configured.") }
            return
        }

        val name = current.editorName.trim().ifEmpty { defaultServerName(normalizedUrl) }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingEditor = true) }
            if (editingId != null) {
                serverStore.updateServer(editingId, name, normalizedUrl)
                // No-ops unless editingId is the currently active server and its URL actually
                // changed -- see AuthRepository.refreshActiveServerUrl for why this is needed at
                // all (an edit here never otherwise reaches AuthRepository's own live state).
                authRepository.refreshActiveServerUrl(editingId)
            } else {
                serverStore.addServer(name, normalizedUrl)
            }
            _uiState.update {
                it.copy(isSavingEditor = false, showEditor = false, editingServerId = null, editorName = "", editorUrl = "")
            }
            load()
        }
    }

    fun requestRemoval(config: HermexServerConfig) {
        _uiState.update { it.copy(serverPendingRemoval = config) }
    }

    fun cancelRemoval() {
        _uiState.update { it.copy(serverPendingRemoval = null) }
    }

    /** Removes the server entirely, including its cookies/custom headers (see
     * [AuthRepository.forgetServer]) -- not just a config-list edit. */
    fun confirmRemoval() {
        val target = _uiState.value.serverPendingRemoval ?: return
        viewModelScope.launch {
            authRepository.forgetServer(target.id)
            _uiState.update { it.copy(serverPendingRemoval = null) }
            load()
        }
    }
}
