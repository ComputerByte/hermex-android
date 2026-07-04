package com.hermex.android.chat

import com.hermex.android.core.network.dto.ChatMessage
import com.hermex.android.core.network.dto.ModelCatalogGroup
import com.hermex.android.core.network.dto.ProfileSummary

data class ChatUiState(
    val isLoading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    /** In-flight assistant token buffer -- rendered as a distinct "live" bubble, merged into
     * [messages] as a finalized [ChatMessage] once the stream ends. */
    val streamingText: String = "",
    val streamingReasoning: String = "",
    val isStreaming: Boolean = false,
    val isSending: Boolean = false,
    val activeToolCalls: List<ToolCallUi> = emptyList(),
    val composerText: String = "",
    val errorMessage: String? = null,
    val profileOptions: List<ProfileSummary> = emptyList(),
    val selectedProfileName: String? = null,
    val isSwitchingProfile: Boolean = false,
    /** Set while waiting on a "start a new session with this profile?" confirmation -- picking a
     * different profile mid-conversation doesn't switch this session in place (matching iOS: an
     * existing transcript stays on its original profile), it offers a fresh one instead. */
    val pendingProfileSwitch: String? = null,
    /** Loaded once from [com.hermex.android.core.storage.ChatPreferencesStore] -- whether a fresh
     * [ReasoningBlock] should start expanded rather than collapsed. */
    val expandThinkingByDefault: Boolean = false,
    /** Loaded once from [com.hermex.android.core.storage.ChatPreferencesStore] -- whether a fresh
     * [ToolCallUi] card should start expanded rather than collapsed. */
    val expandToolCallsByDefault: Boolean = false,
    /** This session's own workspace/model/provider, loaded from [ChatMessage]'s sibling
     * `SessionDetail` when the session loads and kept in sync after every
     * `/api/session/update` -- sent on every `/api/chat/start` so an existing conversation keeps
     * using whatever it was already using (or whatever the composer just switched it to). */
    val currentWorkspace: String? = null,
    val currentModel: String? = null,
    val currentModelProvider: String? = null,
    val modelCatalogGroups: List<ModelCatalogGroup> = emptyList(),
    val isLoadingModelCatalog: Boolean = false,
    /** Covers both the model-switch and (existing) profile-switch API calls -- either one
     * updates this session's live configuration in place. */
    val isUpdatingComposerConfiguration: Boolean = false,
    /** Set to true right after a manual model pick succeeds; sent as `explicit_model_pick: true`
     * on the *next* `/api/chat/start` only, then cleared -- tells the server this model change
     * was deliberate rather than an implicit default, matching iOS exactly. */
    val pendingExplicitModelPick: Boolean = false,
    /** True when [messages] came from the offline cache rather than a successful network fetch --
     * either shown immediately on screen load (before the network result lands) or kept on screen
     * because the network call then failed. */
    val isShowingCachedData: Boolean = false,
    /** User-facing explanation for [isShowingCachedData]. Null whenever [isShowingCachedData] is
     * false. */
    val cacheStatusMessage: String? = null,
    /** Attachments staged for the next [ChatViewModel.sendMessage] call. Dormant in this phase --
     * nothing appends to this list yet, since no file/photo picker exists. Populated via
     * [ChatViewModel.addUploadedAttachment] once one does. */
    val pendingAttachments: List<PendingAttachmentUi> = emptyList(),
    /** True while a future picker's upload call is in flight. Dormant in this phase -- nothing
     * sets this true yet, since [ChatViewModel] never calls `HermexApi.uploadAttachment` itself;
     * exists now so the composer's eventual "uploading" indicator has a single flag to watch. */
    val isUploadingAttachment: Boolean = false,
)

/** A file already uploaded via `HermexApi.uploadAttachment` and staged to go out with the next
 * sent message -- the local, UI-facing mirror of a [com.hermex.android.core.network.dto.MessageAttachment].
 * [id] is a synthetic local key (not server-provided) purely for list operations like
 * [ChatViewModel.removePendingAttachment], since the server shape itself has nothing stable to
 * key on before a message is actually sent. */
data class PendingAttachmentUi(
    val id: String,
    val name: String? = null,
    val path: String? = null,
    val mime: String? = null,
    val size: Long? = null,
    val isImage: Boolean? = null,
)

data class ToolCallUi(
    val stableId: String,
    val name: String?,
    val preview: String?,
    val isComplete: Boolean = false,
    val isError: Boolean = false,
    val durationSeconds: Double? = null,
    /** `messages.size` at the moment this tool call started -- i.e. the index the eventual
     * finalized assistant reply will occupy once the turn completes. Lets the transcript render
     * this card immediately before that message instead of always after every message,
     * regardless of which turn it actually happened in. */
    val anchorMessageCount: Int = 0,
)
