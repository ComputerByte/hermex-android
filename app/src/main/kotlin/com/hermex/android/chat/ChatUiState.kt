package com.hermex.android.chat

import com.hermex.android.core.network.dto.ChatMessage
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
