package com.hermex.android.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.auth.serverIdOrNull
import com.hermex.android.core.cache.NoOpOfflineCacheRepository
import com.hermex.android.core.cache.OfflineCacheRepository
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.HermexApi
import com.hermex.android.core.network.SseEvent
import com.hermex.android.core.network.SseStreamSource
import com.hermex.android.core.network.ToolEventPayload
import com.hermex.android.core.network.chatStreamUrl
import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.core.network.dto.ChatStartRequest
import com.hermex.android.core.network.dto.MessageAttachment
import com.hermex.android.core.network.dto.ModelCatalogOption
import com.hermex.android.core.network.dto.ModelCatalogParser
import com.hermex.android.core.network.dto.NewSessionRequest
import com.hermex.android.core.network.dto.ProfileSwitchRequest
import com.hermex.android.core.network.dto.UpdateSessionRequest
import com.hermex.android.core.network.dto.UploadResponse
import com.hermex.android.core.network.dto.buildAttachedFilesMarker
import com.hermex.android.core.network.dto.capAtChatStartLimit
import com.hermex.android.core.network.dto.mergingLiveModels
import com.hermex.android.core.network.safeApiCall
import com.hermex.android.core.storage.ChatPreferencesStore
import com.hermex.android.core.util.HermexLog
import com.hermex.android.chat.ResponseCompletionNotifier
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import com.hermex.android.core.network.dto.ApprovalRespondRequest
import com.hermex.android.core.network.dto.ClarificationRespondRequest
import com.hermex.android.core.network.dto.SessionYoloRequest
import com.hermex.android.core.network.dto.TruncateSessionRequest
import com.hermex.android.core.network.dto.SessionRenameRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private const val OFFLINE_CACHE_MESSAGE = "Unable to reach server -- showing cached conversation"

/**
 * Drives one chat session: loads recent history, sends a message, opens the SSE stream for the
 * reply, and folds each [SseEvent] into [ChatUiState] as it arrives. Mirrors iOS's
 * `ChatViewModel` streaming state machine, minus reconnect-with-replay (explicitly deferred --
 * see API_CONTRACT.md).
 *
 * TODO(v0.3.0 audit): steer-while-running and approval/clarification are NOT implemented. Neither
 * has a documented endpoint or SSE event shape in API_CONTRACT.md, and [SseEventParser] doesn't
 * decode an `approval`/`clarification` event today (an event named `approval` currently degrades
 * to [SseEvent.Unknown], see `SseEventParserTest`) -- wiring either up would mean inventing a wire
 * contract with no way to verify it. Revisit once the server side documents the real shapes.
 */
class ChatViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    private val sseClient: SseStreamSource,
    private val chatPreferencesStore: ChatPreferencesStore,
    private val offlineCacheRepository: OfflineCacheRepository = NoOpOfflineCacheRepository,
    /** Defaults to an always-fails stub so every existing test/call site that doesn't care about
     * attachments keeps compiling unchanged -- see [AttachmentFileReader]'s doc for why real Uri
     * construction isn't available in this project's plain JVM unit tests anyway. */
    private val attachmentFileReader: AttachmentFileReader = AttachmentFileReader { AttachmentReadResult.Unreadable },
    /** Injected callback for local response-completion notifications. No-op by default so every
     * existing test/call site keeps compiling unchanged. */
    private val responseCompletionNotifier: ResponseCompletionNotifier = ResponseCompletionNotifier { _, _ -> },
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var activeStreamId: String? = null
    private var approvalPollingJob: Job? = null
    private var clarificationPollingJob: Job? = null

    private val currentSessionId: String get() = sessionId

    /** Explicit reason for stream termination -- avoids conflating normal completion
     * (should notify) with user-cancellation or errors (should not notify). */
    private enum class StreamCompletionReason { NORMAL, CANCELLED, ERROR }

    init {
        loadSession()
        viewModelScope.launch {
            val expandThinkingByDefault = chatPreferencesStore.loadExpandThinkingByDefault()
            _uiState.update { it.copy(expandThinkingByDefault = expandThinkingByDefault) }
            val expandToolCallsByDefault = chatPreferencesStore.loadExpandToolCallsByDefault()
            _uiState.update { it.copy(expandToolCallsByDefault = expandToolCallsByDefault) }
        }
        viewModelScope.launch {
            var previousServerId: String? = null
            var sawFirstState = false
            authRepository.state.collect { state ->
                val currentServerId = state.serverIdOrNull
                // Only react to an actual *change* after streaming started -- the first emission
                // here is just this collector's own startup snapshot (StateFlow always replays
                // its current value to a new collector), not a real switch.
                if (sawFirstState && currentServerId != previousServerId && _uiState.value.isStreaming) {
                    HermexLog.w("Chat", "active server changed mid-stream -- closing the local stream")
                    finalizeStream(errorMessage = "The active server changed, so this run was stopped.", reason = StreamCompletionReason.CANCELLED)
                }
                previousServerId = currentServerId
                sawFirstState = true
            }
        }
    }

    /** Runs after the session load, in the same coroutine rather than a concurrent one, so the
     * two requests hit the server in a fixed order (session, then profiles) -- deterministic for
     * tests against a single-queue MockWebServer, and irrelevant in production either way. */
    private suspend fun loadProfiles(api: HermexApi) {
        try {
            val response = safeApiCall { api.profiles() }
            _uiState.update {
                it.copy(
                    profileOptions = response.profiles.orEmpty(),
                    selectedProfileName = response.effectiveDefaultProfileName,
                )
            }
        } catch (e: ApiError) {
            // Best-effort: the composer profile selector just stays empty/disabled. Not worth
            // surfacing as a hard error alongside the session transcript itself.
            HermexLog.w("Chat", "Could not load profiles: ${e.message}")
        }
    }

    /** Shows the offline cache immediately (if there is one) while the network fetch is still in
     * flight -- reopening a session you've read before doesn't start from a blank transcript --
     * then a successful fetch replaces it with fresh data (and refreshes the cache for next
     * time), while a failed fetch just leaves the cached messages on screen with
     * [ChatUiState.cacheStatusMessage] explaining why. If there's no cache at all, a failed fetch
     * falls through to the existing plain error state. */
    fun loadSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val serverId = authRepository.activeServerId()
            if (serverId != null) {
                val cached = offlineCacheRepository.cachedSessionDetail(serverId, sessionId)
                if (cached != null) {
                    _uiState.update {
                        it.copy(
                            messages = cached.messages.orEmpty(),
                            currentWorkspace = cached.workspace,
                            currentModel = cached.model,
                            currentModelProvider = cached.modelProvider,
                            isShowingCachedData = true,
                            cacheStatusMessage = OFFLINE_CACHE_MESSAGE,
                        )
                    }
                }
            }
            val api = authRepository.apiForActiveServer()
            if (api == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Not signed in.") }
                return@launch
            }
            try {
                val response = safeApiCall { api.session(sessionId = sessionId, messages = 1, msgLimit = 50) }
                val session = response.session
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        messages = session?.messages.orEmpty(),
                        currentWorkspace = session?.workspace,
                        currentModel = session?.model,
                        currentModelProvider = session?.modelProvider,
                        isShowingCachedData = false,
                        cacheStatusMessage = null,
                        errorMessage = null,
                        hasDisconnectedStream = session?.activeStreamId != null && activeStreamId == null,
                        disconnectedStreamId = if (session?.activeStreamId != null && activeStreamId == null) session.activeStreamId else null,
                    )
                }
                if (serverId != null && session != null) {
                    viewModelScope.launch { offlineCacheRepository.cacheSessionDetail(serverId, sessionId, session) }
                }
            } catch (e: ApiError) {
                val hasCache = _uiState.value.isShowingCachedData
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (hasCache) null else (e.message ?: "Could not load session."),
                        cacheStatusMessage = if (hasCache) OFFLINE_CACHE_MESSAGE else null,
                    )
                }
            }
            loadProfiles(api)
        }
    }

    /** Refetches `/api/models` (so the picker stops pinning the chat-load-time snapshot), then
     * overlays the active provider's live list -- both best-effort, matching iOS's
     * `refreshModelCatalogForPickerOpen`: a failure just means the picker keeps whatever it
     * already had. */
    fun refreshModelCatalogForPickerOpen() {
        val api = authRepository.apiForActiveServer() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModelCatalog = true) }
            try {
                val response = safeApiCall { api.models() }
                val groups = ModelCatalogParser.parseGroups(response)
                if (groups.isNotEmpty()) {
                    _uiState.update { it.copy(modelCatalogGroups = groups) }
                }
            } catch (e: ApiError) {
                HermexLog.w("Chat", "Could not refresh model catalog: ${e.message}")
            }
            val live = runCatching { safeApiCall { api.modelsLive() } }.getOrNull()
            if (live != null) {
                _uiState.update { it.copy(modelCatalogGroups = it.modelCatalogGroups.mergingLiveModels(live)) }
            }
            _uiState.update { it.copy(isLoadingModelCatalog = false) }
        }
    }

    /** Updates *this* session's model/provider in place via `/api/session/update` -- unlike
     * profile switching, this never starts a new session. Blocked while a stream is actively
     * running (matching iOS's `activeStreamID == nil` guard); a no-op if [option] is already the
     * current selection. */
    fun selectComposerModel(option: ModelCatalogOption) {
        val state = _uiState.value
        if (option.matchesSelection(state.currentModel, state.currentModelProvider)) return
        if (state.isStreaming) {
            _uiState.update { it.copy(errorMessage = "Wait for the current response to finish before changing models.") }
            return
        }
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(errorMessage = "Not signed in.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingComposerConfiguration = true, errorMessage = null) }
            try {
                val response = safeApiCall {
                    api.updateSession(
                        UpdateSessionRequest(
                            sessionId = sessionId,
                            workspace = state.currentWorkspace,
                            model = option.id,
                            modelProvider = option.providerId,
                        ),
                    )
                }
                _uiState.update {
                    it.copy(
                        isUpdatingComposerConfiguration = false,
                        currentModel = response.session?.model ?: option.id,
                        currentModelProvider = response.session?.modelProvider ?: option.providerId,
                        currentWorkspace = response.session?.workspace ?: it.currentWorkspace,
                        pendingExplicitModelPick = true,
                    )
                }
            } catch (e: ApiError) {
                // Keeps the previous model/provider on failure -- only the error surfaces.
                _uiState.update {
                    it.copy(isUpdatingComposerConfiguration = false, errorMessage = e.message ?: "Could not change model.")
                }
            }
        }
    }

    fun onComposerTextChanged(value: String) {
        _uiState.update { it.copy(composerText = value) }
    }

    /** Stages externally supplied text (Android share sheet) for user review. This deliberately
     * never sends; it only pre-fills an empty composer so the user can edit or discard first. */
    fun stageDraftIfComposerEmpty(value: String) {
        val draft = value.trim()
        if (draft.isEmpty()) return
        _uiState.update { state ->
            if (state.composerText.isBlank()) state.copy(composerText = draft) else state
        }
    }

    /** Folds a completed upload's response into [ChatUiState.pendingAttachments]. Called by
     * [performAttachmentUpload] after a real `HermexApi.uploadAttachment()` succeeds; kept
     * `internal` rather than `private` so tests can stage pending attachments directly without a
     * real upload/network call. A [response] carrying [UploadResponse.error] surfaces that error
     * the same way other composer failures do, without ever entering the pending list. */
    internal fun addUploadedAttachment(response: UploadResponse) {
        if (response.error != null) {
            _uiState.update { it.copy(errorMessage = response.error) }
            return
        }
        val attachment = PendingAttachmentUi(
            id = UUID.randomUUID().toString(),
            name = response.filename,
            path = response.path,
            mime = response.mime,
            size = response.size,
            isImage = response.isImage,
        )
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + attachment) }
    }

    /** No-op if [id] doesn't match anything currently pending (e.g. a stale UI callback after the
     * list already changed) -- safe to call unconditionally. */
    fun removePendingAttachment(id: String) {
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments.filterNot { attachment -> attachment.id == id }) }
    }

    /** The composer's real attach-button entry point: reads [uri] via [attachmentFileReader], then
     * uploads it through [performAttachmentUpload]. One upload at a time is enough for this MVP --
     * a second tap while one is already in flight is a no-op rather than queued. No-ops with a
     * "reconnect" error instead of calling the server at all when showing cached data offline,
     * matching [cancelStream]'s and [selectComposerModel]'s existing pattern. */
    fun uploadAttachment(uri: Uri) {
        val state = _uiState.value
        if (state.isUploadingAttachment) return
        if (state.isShowingCachedData) {
            _uiState.update { it.copy(errorMessage = "Reconnect to the server to upload attachments.") }
            return
        }
        if (authRepository.apiForActiveServer() == null) {
            _uiState.update { it.copy(errorMessage = "Not signed in.") }
            return
        }
        viewModelScope.launch {
            uploadAttachmentSuspend(uri)
        }
    }

    /** Suspend variant that completes when the single upload is done. Shared between
     * [uploadAttachment] (which launches it in a coroutine) and [uploadAttachmentsSequentially]
     * (which calls it in a loop). */
    private suspend fun uploadAttachmentSuspend(uri: Uri) {
        _uiState.update { it.copy(isUploadingAttachment = true, errorMessage = null) }
        when (val result = attachmentFileReader.read(uri)) {
            is AttachmentReadResult.TooLarge -> _uiState.update {
                it.copy(
                    isUploadingAttachment = false,
                    errorMessage = "${result.name} is too large. Attachments must be 20 MB or smaller.",
                )
            }
            AttachmentReadResult.Unreadable -> _uiState.update {
                it.copy(isUploadingAttachment = false, errorMessage = "Could not read the selected file.")
            }
            is AttachmentReadResult.Success -> performAttachmentUpload(result.file)
        }
    }

    /**
     * Uploads multiple shared files sequentially. Each file is read, uploaded to the server, and
     * staged as a pending attachment before the next one starts. A single file failure (too large,
     * unreadable, server error) does not block the remaining files -- it surfaces an error message
     * and continues. Does not auto-send; the user must tap Send to dispatch.
     */
    fun uploadAttachmentsSequentially(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (_uiState.value.isShowingCachedData) {
            _uiState.update { it.copy(errorMessage = "Reconnect to the server to upload attachments.") }
            return
        }
        if (authRepository.apiForActiveServer() == null) {
            _uiState.update { it.copy(errorMessage = "Not signed in.") }
            return
        }
        viewModelScope.launch {
            for (uri in uris) {
                uploadAttachmentSuspend(uri)
                // Wait for the current upload to settle before moving to the next
                _uiState.first { !it.isUploadingAttachment }
            }
        }
    }

    /** The actual `/api/upload` call and the state fold-in -- split out from [uploadAttachment] so
     * tests can exercise this network/state logic directly with a plain [AttachmentFile], without
     * ever needing a real `android.net.Uri` (see [AttachmentFileReader]'s doc). `internal` rather
     * than `private` for exactly that reason. Sets [ChatUiState.isUploadingAttachment] itself
     * (redundantly true if [uploadAttachment] already set it while reading the file) so it's
     * fully self-contained for a test calling it directly. */
    internal suspend fun performAttachmentUpload(file: AttachmentFile) {
        _uiState.update { it.copy(isUploadingAttachment = true, errorMessage = null) }
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(isUploadingAttachment = false, errorMessage = "Not signed in.") }
            return
        }
        try {
            val response = safeApiCall {
                api.uploadAttachment(
                    sessionId = sessionId.toRequestBody(),
                    file = MultipartBody.Part.createFormData(
                        "file",
                        file.name,
                        file.bytes.toRequestBody((file.mime ?: "application/octet-stream").toMediaTypeOrNull()),
                    ),
                )
            }
            // Fold the response into pendingAttachments/errorMessage *before* clearing the busy
            // flag -- these are two separate _uiState.update calls, and a collector watching for
            // "upload settled" (isUploadingAttachment turning false) must never observe that
            // ahead of the attachment it was actually waiting on actually showing up.
            addUploadedAttachment(response)
            _uiState.update { it.copy(isUploadingAttachment = false) }
        } catch (e: ApiError) {
            _uiState.update { it.copy(isUploadingAttachment = false, errorMessage = e.message ?: "Could not upload the attachment.") }
        }
    }

    /** Picking a profile from the composer selector. An empty transcript just switches the
     * server's active profile in place; once messages exist, switching profiles instead offers
     * to start a fresh session on that profile (see [pendingProfileSwitch] and
     * [confirmPendingProfileSwitch]), matching iOS. */
    fun selectProfile(name: String) {
        val state = _uiState.value
        if (name == state.selectedProfileName || state.isSwitchingProfile) return
        if (state.messages.isEmpty()) {
            performProfileSwitch(name)
        } else {
            _uiState.update { it.copy(pendingProfileSwitch = name) }
        }
    }

    fun dismissPendingProfileSwitch() {
        _uiState.update { it.copy(pendingProfileSwitch = null) }
    }

    /** Confirms starting a new session on [ChatUiState.pendingProfileSwitch]'s profile. Switches
     * the server's active profile, creates a new session on it, and hands the new session id to
     * [onNewSession] so the caller can navigate there -- this view model has no navigation
     * authority of its own. */
    fun confirmPendingProfileSwitch(onNewSession: (String) -> Unit) {
        val name = _uiState.value.pendingProfileSwitch ?: return
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(pendingProfileSwitch = null, errorMessage = "Not signed in.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(pendingProfileSwitch = null, isSwitchingProfile = true, errorMessage = null) }
            try {
                safeApiCall { api.switchProfile(ProfileSwitchRequest(name)) }
                val sessionResponse = safeApiCall { api.newSession(NewSessionRequest(profile = name)) }
                val newSessionId = sessionResponse.session?.sessionId
                _uiState.update { it.copy(isSwitchingProfile = false, selectedProfileName = name) }
                if (newSessionId != null) {
                    onNewSession(newSessionId)
                } else {
                    _uiState.update { it.copy(errorMessage = "Server did not return a session id.") }
                }
            } catch (e: ApiError) {
                _uiState.update {
                    it.copy(isSwitchingProfile = false, errorMessage = e.message ?: "Could not switch profile.")
                }
            }
        }
    }

    private fun performProfileSwitch(name: String) {
        val api = authRepository.apiForActiveServer() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSwitchingProfile = true, errorMessage = null) }
            try {
                val response = safeApiCall { api.switchProfile(ProfileSwitchRequest(name)) }
                if (response.error != null) {
                    _uiState.update { it.copy(isSwitchingProfile = false, errorMessage = response.error) }
                } else {
                    _uiState.update {
                        it.copy(
                            isSwitchingProfile = false,
                            profileOptions = response.profiles ?: it.profileOptions,
                            selectedProfileName = response.active ?: name,
                        )
                    }
                }
            } catch (e: ApiError) {
                _uiState.update {
                    it.copy(isSwitchingProfile = false, errorMessage = e.message ?: "Could not switch profile.")
                }
            }
        }
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
            val state = _uiState.value
            // Captured now, not re-read on completion: if the user picks a *different* model
            // while this request is in flight, that pick's own true value must win, not get
            // clobbered by this (now-stale) request's completion. Only sent when there's an
            // actual current model to attribute the pick to, matching iOS's
            // `explicitModelPickForChatStart`.
            val explicitModelPick = state.pendingExplicitModelPick && !state.currentModel.isNullOrBlank()
            // Capped the same way the server itself caps them (`_normalize_chat_attachments(...)[:20]`,
            // V5 Phase 5 recon) -- deriving the marker from this same capped list, not the
            // uncapped one, keeps the marker and the structured `attachments` field in agreement
            // about which files actually went out.
            val cappedAttachments = state.pendingAttachments.map { it.toMessageAttachment() }.capAtChatStartLimit()
            val attachmentReferences = cappedAttachments.mapNotNull { it.chatReference() }
            val messageForApi = text + buildAttachedFilesMarker(attachmentReferences)
            _uiState.update { it.copy(isSending = true, errorMessage = null) }

            val startResponse = try {
                safeApiCall {
                    api.chatStart(
                        ChatStartRequest(
                            sessionId = sessionId,
                            message = messageForApi,
                            workspace = state.currentWorkspace,
                            model = state.currentModel,
                            modelProvider = state.currentModelProvider?.takeIf { it.isNotBlank() },
                            profile = state.selectedProfileName?.takeIf { it.isNotBlank() },
                            explicitModelPick = if (explicitModelPick) true else null,
                            attachments = cappedAttachments.ifEmpty { null },
                        ),
                    )
                }
            } catch (e: ApiError.Network) {
                // A connectivity/IO failure specifically (see ApiError.Network) -- never queued,
                // never shown as if it sent; the composer keeps the unsent text so nothing is lost.
                _uiState.update { it.copy(isSending = false, errorMessage = "You're offline. Reconnect to send messages.") }
                return@launch
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
            // Uses messageForApi (marker included, same as what the server will persist and
            // eventually echo back on reload) rather than the bare typed text, so this optimistic
            // bubble already matches what a reload would show -- display-layer marker stripping
            // is a separate, not-yet-built concern (see MessageAttachment's iOS equivalent).
            val userMessage = ChatMessage(
                role = "user",
                content = messageForApi,
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
                    pendingExplicitModelPick = if (explicitModelPick) false else it.pendingExplicitModelPick,
                    // Only cleared on this success path -- a failed send (any return@launch above)
                    // leaves pendingAttachments untouched, matching the composer text's own
                    // failure behavior.
                    pendingAttachments = emptyList(),
                )
            }

            _uiState.update { it.copy(hasDisconnectedStream = false, disconnectedStreamId = null) }
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
            is SseEvent.Token -> {
                // Logged once per turn, not per token -- streamingText is empty only for the very
                // first token, so this can't fire again until the next finalizeStream resets it.
                if (_uiState.value.streamingText.isEmpty()) HermexLog.d("Chat", "first token received")
                _uiState.update { it.copy(streamingText = it.streamingText + event.text) }
            }
            is SseEvent.Reasoning -> _uiState.update { it.copy(streamingReasoning = it.streamingReasoning + event.text) }
            is SseEvent.ToolStarted -> upsertToolCall(event.payload, completed = false)
            is SseEvent.ToolCompleted -> upsertToolCall(event.payload, completed = true)
            is SseEvent.Done -> {
                HermexLog.d("Chat", "event: done")
                finalizeStream(reason = StreamCompletionReason.NORMAL)
            }
            SseEvent.StreamEnd -> {
                HermexLog.d("Chat", "event: stream_end")
                finalizeStream(reason = StreamCompletionReason.NORMAL)
            }
            SseEvent.Cancelled -> finalizeStream(reason = StreamCompletionReason.CANCELLED)
            is SseEvent.Error -> finalizeStream(errorMessage = event.message, reason = StreamCompletionReason.ERROR)
            is SseEvent.TransportError -> finalizeStream(errorMessage = event.message, reason = StreamCompletionReason.ERROR)
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
                    rawArgs = payload.args?.let { kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), it) } ?: existing.rawArgs,
                )
            } else {
                ToolCallUi(
                    stableId = stableId,
                    name = payload.name,
                    preview = payload.preview,
                    isComplete = completed,
                    isError = payload.isError ?: false,
                    durationSeconds = payload.duration,
                    rawArgs = payload.args?.let { kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), it) },
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
     * turn since [streamJob] is cancelled here too, so a late event after finalize is a no-op.
     * Fires the [responseCompletionNotifier] after state update so it observes the final UI state. */
    private fun finalizeStream(errorMessage: String? = null, reason: StreamCompletionReason = StreamCompletionReason.CANCELLED) {
        HermexLog.d("Chat", "finalizeStream" + (errorMessage?.let { " (error: $it)" } ?: ""))
        streamJob?.cancel()
        streamJob = null
        activeStreamId = null
        approvalPollingJob?.cancel()
        approvalPollingJob = null
        clarificationPollingJob?.cancel()
        clarificationPollingJob = null
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
                pendingApproval = null,
                isRespondingToApproval = false,
                approvalErrorMessage = null,
                pendingClarification = null,
                isRespondingToClarification = false,
                clarificationErrorMessage = null,
            )
        }
        HermexLog.d("Chat", "isRunning=false messages=${_uiState.value.messages.size}")
        refreshCacheInBackground()
        if (reason == StreamCompletionReason.NORMAL) {
            responseCompletionNotifier.onResponseCompleted(sessionId, completedNormally = true)
        }
    }

    /** Re-fetches this session's detail from the network and caches *that* -- deliberately never
     * writes the locally-finalized [ChatUiState] (streamed text, the optimistic user message)
     * directly into the offline cache, so a partial/synthetic reply can never end up stored as if
     * it were final. Silent: the turn has already finished from the user's point of view, so a
     * failed background refresh here isn't worth surfacing as an error. */
    private fun refreshCacheInBackground() {
        val serverId = authRepository.activeServerId() ?: return
        val api = authRepository.apiForActiveServer() ?: return
        viewModelScope.launch {
            val session = runCatching { safeApiCall { api.session(sessionId = sessionId, messages = 1, msgLimit = 50) } }
                .getOrNull()?.session ?: return@launch
            offlineCacheRepository.cacheSessionDetail(serverId, sessionId, session)
        }
    }

    fun startApprovalPolling(sessionId: String) {
        approvalPollingJob?.cancel()
        approvalPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(2000L)
                try {
                    val api = authRepository.apiForActiveServer() ?: break
                    val response = safeApiCall { api.approvalPending(sessionId) }
                    val pending = response.pending
                    if (pending != null && !pending.approvalId.isNullOrBlank()) {
                        _uiState.update { it.copy(
                            pendingApproval = PendingApprovalUi(
                                approvalId = pending.approvalId,
                                command = pending.command,
                                description = pending.description,
                                patternKeys = (pending.patternKeys ?: pending.patternKey?.let { listOf(it) } ?: emptyList()),
                                pendingCount = response.pendingCount,
                            ),
                            approvalErrorMessage = null,
                        )}
                    } else {
                        _uiState.update { it.copy(pendingApproval = null) }
                    }
                } catch (_: Exception) {
                    // Polling failures are non-fatal.
                }
            }
        }
    }

    fun startClarificationPolling(sessionId: String) {
        clarificationPollingJob?.cancel()
        clarificationPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(2000L)
                try {
                    val api = authRepository.apiForActiveServer() ?: break
                    val response = safeApiCall { api.clarifyPending(sessionId) }
                    val pending = response.pending
                    if (pending != null && !pending.clarifyId.isNullOrBlank()) {
                        _uiState.update { it.copy(
                            pendingClarification = PendingClarificationUi(
                                clarifyId = pending.clarifyId,
                                question = pending.question ?: "The agent needs more information.",
                                choices = pending.choicesOffered?.filter { it.isNotBlank() } ?: emptyList(),
                                sessionId = pending.sessionId,
                                timeoutSeconds = pending.timeoutSeconds,
                                expiresAt = pending.expiresAt,
                                pendingCount = response.pendingCount ?: 1,
                            ),
                            clarificationErrorMessage = null,
                        )}
                    } else {
                        _uiState.update { it.copy(pendingClarification = null) }
                    }
                } catch (_: Exception) {
                    // Non-fatal.
                }
            }
        }
    }

    fun respondToApproval(choice: String) {
        val approval = _uiState.value.pendingApproval ?: return
        val sessionId = currentSessionId
        _uiState.update { it.copy(isRespondingToApproval = true, approvalErrorMessage = null) }
        viewModelScope.launch {
            try {
                val api = authRepository.apiForActiveServer()
                if (api == null) {
                    _uiState.update { it.copy(isRespondingToApproval = false, approvalErrorMessage = "Not signed in.") }
                    return@launch
                }
                safeApiCall {
                    api.approvalRespond(ApprovalRespondRequest(
                        sessionId = sessionId,
                        choice = choice,
                        approvalId = approval.approvalId,
                    ))
                }
                _uiState.update { it.copy(pendingApproval = null, isRespondingToApproval = false) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(
                    isRespondingToApproval = false,
                    approvalErrorMessage = e.message ?: "Could not respond.",
                )}
            }
        }
    }

    fun skipAllApprovals() {
        val sessionId = currentSessionId
        _uiState.update { it.copy(isRespondingToApproval = true, approvalErrorMessage = null) }
        viewModelScope.launch {
            try {
                val api = authRepository.apiForActiveServer()
                if (api == null) {
                    _uiState.update { it.copy(isRespondingToApproval = false, approvalErrorMessage = "Not signed in.") }
                    return@launch
                }
                safeApiCall {
                    api.sessionYoloSet(SessionYoloRequest(sessionId = sessionId, enabled = true))
                }
                _uiState.update { it.copy(
                    pendingApproval = null,
                    isRespondingToApproval = false,
                    isSessionApprovalBypassEnabled = true,
                )}
            } catch (e: ApiError) {
                _uiState.update { it.copy(
                    isRespondingToApproval = false,
                    approvalErrorMessage = e.message ?: "Could not skip approvals.",
                )}
            }
        }
    }

    fun respondToClarification(response: String) {
        val clarification = _uiState.value.pendingClarification ?: return
        val sessionId = currentSessionId
        val trimmed = response.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(isRespondingToClarification = true, clarificationErrorMessage = null) }
        viewModelScope.launch {
            try {
                val api = authRepository.apiForActiveServer()
                if (api == null) {
                    _uiState.update { it.copy(isRespondingToClarification = false, clarificationErrorMessage = "Not signed in.") }
                    return@launch
                }
                safeApiCall {
                    api.clarifyRespond(ClarificationRespondRequest(
                        sessionId = sessionId,
                        response = trimmed,
                        clarifyId = clarification.clarifyId,
                    ))
                }
                _uiState.update { it.copy(pendingClarification = null, isRespondingToClarification = false) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(
                    isRespondingToClarification = false,
                    clarificationErrorMessage = e.message ?: "Could not respond.",
                )}
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                val api = authRepository.apiForActiveServer() ?: throw ApiError.Network(Exception("Not signed in"))
                safeApiCall { api.renameSession(SessionRenameRequest(sessionId, newTitle)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Could not rename session.") }
            }
        }
    }

    fun regenerate() {
        val messages = _uiState.value.messages
        if (messages.size < 2) return
        val lastMessage = messages.lastOrNull() ?: return
        if (lastMessage.role == "user") return
        viewModelScope.launch {
            try {
                val api = authRepository.apiForActiveServer() ?: throw ApiError.Network(Exception("Not signed in"))
                val keepCount = messages.size - 1
                safeApiCall { api.truncateSession(TruncateSessionRequest(session_id = sessionId, keep_count = keepCount)) }
                val lastUserText = messages.dropLast(1).lastOrNull()?.content ?: return@launch
                _uiState.update { it.copy(messages = it.messages.dropLast(1), composerText = lastUserText) }
                sendMessage()
            } catch (e: ApiError) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Could not regenerate.") }
            }
        }
    }

    fun editMessage(index: Int) {
        val text = _uiState.value.messages.getOrNull(index)?.content ?: return
        _uiState.update { it.copy(composerText = text) }
    }

    fun retryLastMessage() {
        val messages = _uiState.value.messages
        val lastUserIdx = messages.indexOfLast { it.role == "user" }
        if (lastUserIdx < 0) return
        val lastUserText = messages[lastUserIdx].content.orEmpty()
        viewModelScope.launch {
            try {
                val api = authRepository.apiForActiveServer() ?: throw ApiError.Network(Exception("Not signed in"))
                val keepCount = lastUserIdx
                safeApiCall { api.truncateSession(TruncateSessionRequest(session_id = sessionId, keep_count = keepCount)) }
                _uiState.update { it.copy(messages = it.messages.take(keepCount), composerText = lastUserText) }
                sendMessage()
            } catch (e: ApiError) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Could not retry.") }
            }
        }
    }

    /** Stop button. Fires a cancel request to the server, and finalizes locally immediately
     * rather than waiting for a `cancel` event to arrive on the stream, so the UI feels instant.
     * The local finalize happens either way (matching iOS -- a stuck "stop" button that keeps
     * waiting on a flaky server is worse than a possibly-still-running server turn the user can
     * no longer see); a failed cancel just surfaces an error afterward without reopening the
     * stream or touching [ChatUiState.messages]. No-ops with a "reconnect" error instead of
     * calling the server at all when showing cached data offline -- see [ChatUiState.isShowingCachedData]. */
    fun reattachStream() {
        val streamId = _uiState.value.disconnectedStreamId ?: return
        val serverBaseUrl = authRepository.activeServerBaseUrl() ?: return
        _uiState.update { it.copy(isReattaching = true, hasDisconnectedStream = false) }
        activeStreamId = streamId
        observeStream(serverBaseUrl, streamId)
        _uiState.update { it.copy(isReattaching = false) }
    }

    fun cancelStream() {
        if (_uiState.value.isShowingCachedData) {
            _uiState.update { it.copy(errorMessage = "Reconnect to control this run.") }
            return
        }
        val streamId = activeStreamId ?: return
        HermexLog.d("Chat", "cancelStream: streamId=$streamId")
        val api = authRepository.apiForActiveServer()
        if (api != null) {
            viewModelScope.launch {
                try {
                    val response = safeApiCall { api.chatCancel(streamId) }
                    if (response.error != null) {
                        _uiState.update { it.copy(errorMessage = response.error) }
                    }
                } catch (e: ApiError) {
                    HermexLog.w("Chat", "chat/cancel failed: ${e.message}")
                    _uiState.update {
                        it.copy(errorMessage = e.message ?: "Could not confirm the stop with the server.")
                    }
                }
            }
        }
        finalizeStream(reason = StreamCompletionReason.CANCELLED)
    }

    override fun onCleared() {
        streamJob?.cancel()
    }

    private fun nowEpochSeconds(): Double = System.currentTimeMillis() / 1000.0

    private fun PendingAttachmentUi.toMessageAttachment(): MessageAttachment = MessageAttachment(
        name = name,
        path = path,
        mime = mime,
        size = size,
        isImage = isImage,
    )

    /** Matches iOS's `PendingAttachment.chatReference`: prefer the server-assigned upload path,
     * falling back to the display name only when there's no path at all. */
    private fun MessageAttachment.chatReference(): String? =
        path?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }
}
