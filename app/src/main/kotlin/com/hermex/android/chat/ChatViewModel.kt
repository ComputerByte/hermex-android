package com.hermex.android.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.SseEvent
import com.hermex.android.core.network.SseStreamSource
import com.hermex.android.core.network.ToolEventPayload
import com.hermex.android.core.network.chatStreamUrl
import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.core.network.dto.ChatStartRequest
import com.hermex.android.core.network.safeApiCall
import com.hermex.android.core.util.HermexLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives one chat session: loads recent history, sends a message, opens the SSE stream for the
 * reply, and folds each [SseEvent] into [ChatUiState] as it arrives. Mirrors iOS's
 * `ChatViewModel` streaming state machine, minus reconnect-with-replay (explicitly deferred --
 * see API_CONTRACT.md) and the approval/clarification/attachment features out of MVP scope.
 */
class ChatViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    private val sseClient: SseStreamSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var activeStreamId: String? = null

    init {
        loadSession()
    }

    fun loadSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val api = authRepository.apiForActiveServer()
            if (api == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Not signed in.") }
                return@launch
            }
            try {
                val response = safeApiCall { api.session(sessionId = sessionId, messages = 1, msgLimit = 50) }
                _uiState.update { it.copy(isLoading = false, messages = response.session?.messages.orEmpty()) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Could not load session.") }
            }
        }
    }

    fun onComposerTextChanged(value: String) {
        _uiState.update { it.copy(composerText = value) }
    }

    fun sendMessage() {
        val text = _uiState.value.composerText.trim()
        if (text.isEmpty() || _uiState.value.isSending || _uiState.value.isStreaming) return

        val api = authRepository.apiForActiveServer()
        val serverBaseUrl = authRepository.activeServerBaseUrl()
        if (api == null || serverBaseUrl == null) {
            _uiState.update { it.copy(errorMessage = "Not signed in.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }

            val startResponse = try {
                safeApiCall { api.chatStart(ChatStartRequest(sessionId = sessionId, message = text)) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isSending = false, errorMessage = e.message ?: "Could not send message.") }
                return@launch
            }

            val streamId = startResponse.streamId
            if (streamId == null) {
                HermexLog.w("Chat", "chat/start returned no stream_id: ${startResponse.error}")
                _uiState.update {
                    it.copy(isSending = false, errorMessage = startResponse.error ?: "Server did not start a stream.")
                }
                return@launch
            }
            HermexLog.d("Chat", "chat/start ok, streamId=$streamId -- opening SSE")

            // Append the user's own message immediately (optimistic) and clear the composer.
            val userMessage = ChatMessage(
                role = "user",
                content = text,
                timestamp = nowEpochSeconds(),
            )
            _uiState.update {
                it.copy(
                    isSending = false,
                    isStreaming = true,
                    composerText = "",
                    messages = it.messages + userMessage,
                    streamingText = "",
                    streamingReasoning = "",
                    // NOT resetting activeToolCalls here: each entry is anchored to the message
                    // index it belongs to (see ToolCallUi.anchorMessageCount / ChatScreen), so
                    // past turns' tool cards must persist across a new send to stay visible in
                    // their correct position in the transcript.
                )
            }

            activeStreamId = streamId
            observeStream(serverBaseUrl, streamId)
        }
    }

    private fun observeStream(serverBaseUrl: String, streamId: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            sseClient.stream(chatStreamUrl(serverBaseUrl, streamId)).collect(::handleEvent)
        }
    }

    private fun handleEvent(event: SseEvent) {
        when (event) {
            is SseEvent.Token -> _uiState.update { it.copy(streamingText = it.streamingText + event.text) }
            is SseEvent.Reasoning -> _uiState.update { it.copy(streamingReasoning = it.streamingReasoning + event.text) }
            is SseEvent.ToolStarted -> upsertToolCall(event.payload, completed = false)
            is SseEvent.ToolCompleted -> upsertToolCall(event.payload, completed = true)
            is SseEvent.Done -> finalizeStream()
            SseEvent.StreamEnd -> finalizeStream()
            SseEvent.Cancelled -> finalizeStream()
            is SseEvent.Error -> finalizeStream(errorMessage = event.message)
            is SseEvent.TransportError -> finalizeStream(errorMessage = event.message)
            // Unrecognized event names, or a heartbeat/no-op the parser already swallowed --
            // never surfaced to the UI.
            SseEvent.Unknown -> Unit
        }
    }

    private fun upsertToolCall(payload: ToolEventPayload, completed: Boolean) {
        // A tool event with no identifiable id at all can't be tracked or later matched to its
        // completion -- dropping it silently is safer than crashing or corrupting another card.
        val stableId = payload.stableId ?: return
        _uiState.update { state ->
            val existingIndex = state.activeToolCalls.indexOfFirst { it.stableId == stableId }
            val merged = if (existingIndex >= 0) {
                val existing = state.activeToolCalls[existingIndex]
                existing.copy(
                    name = payload.name ?: existing.name,
                    preview = payload.preview ?: existing.preview,
                    isComplete = completed,
                    isError = payload.isError ?: existing.isError,
                    durationSeconds = payload.duration ?: existing.durationSeconds,
                )
            } else {
                ToolCallUi(
                    stableId = stableId,
                    name = payload.name,
                    preview = payload.preview,
                    isComplete = completed,
                    isError = payload.isError ?: false,
                    durationSeconds = payload.duration,
                    anchorMessageCount = state.messages.size,
                )
            }
            val updatedList = if (existingIndex >= 0) {
                state.activeToolCalls.toMutableList().apply { set(existingIndex, merged) }
            } else {
                state.activeToolCalls + merged
            }
            state.copy(activeToolCalls = updatedList)
        }
    }

    /** Finalizes the in-flight assistant message (if any) into [ChatUiState.messages], clears
     * the streaming buffers, and stops the stream. Called on every terminal SSE event
     * (done/stream_end/cancel/error) and on a local cancel -- always reachable exactly once per
     * turn since [streamJob] is cancelled here too, so a late event after finalize is a no-op. */
    private fun finalizeStream(errorMessage: String? = null) {
        HermexLog.d("Chat", "finalizeStream" + (errorMessage?.let { " (error: $it)" } ?: ""))
        streamJob?.cancel()
        streamJob = null
        activeStreamId = null
        _uiState.update { state ->
            val hasPartialReply = state.streamingText.isNotEmpty() || state.streamingReasoning.isNotEmpty()
            val finalizedMessages = if (hasPartialReply) {
                state.messages + ChatMessage(
                    role = "assistant",
                    content = state.streamingText.ifEmpty { null },
                    reasoning = state.streamingReasoning.ifEmpty { null },
                    timestamp = nowEpochSeconds(),
                )
            } else {
                state.messages
            }
            state.copy(
                messages = finalizedMessages,
                streamingText = "",
                streamingReasoning = "",
                isStreaming = false,
                errorMessage = errorMessage ?: state.errorMessage,
            )
        }
    }

    /** Stop button. Fires a best-effort cancel request to the server, and finalizes locally
     * immediately rather than waiting for a `cancel` event to arrive on the stream, so the UI
     * feels instant. */
    fun cancelStream() {
        val streamId = activeStreamId ?: return
        HermexLog.d("Chat", "cancelStream: streamId=$streamId")
        val api = authRepository.apiForActiveServer()
        if (api != null) {
            viewModelScope.launch {
                runCatching { safeApiCall { api.chatCancel(streamId) } }
            }
        }
        finalizeStream()
    }

    override fun onCleared() {
        streamJob?.cancel()
    }

    private fun nowEpochSeconds(): Double = System.currentTimeMillis() / 1000.0
}
