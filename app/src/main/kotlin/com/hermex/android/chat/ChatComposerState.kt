package com.hermex.android.chat

/**
 * Derived, read-only composer state -- consolidates the busy/disabled booleans that used to be
 * threaded into [ChatComposer] individually (`isSending`, `isStreaming`, `isSwitchingProfile`,
 * `isUpdatingComposerConfiguration`) into one place, computed from [ChatUiState]. Exists so a
 * future composer feature (attachments, slash commands) has one state object to extend or gate
 * against instead of re-deriving this same set of flags itself.
 *
 * Deliberately holds no callbacks -- pure data, mirroring [ChatUiState] itself.
 */
data class ChatComposerState(
    val text: String,
    val isSending: Boolean,
    val isStreaming: Boolean,
    val isSwitchingProfile: Boolean,
    val isUpdatingComposerConfiguration: Boolean,
) {
    /** Matches the pre-existing `OutlinedTextField(enabled = !isSending && !isStreaming)` check. */
    val isTextFieldEnabled: Boolean get() = !isSending && !isStreaming

    /** Matches the pre-existing trailing-slot `when` branch order exactly: Stop wins whenever
     * streaming, the sending spinner only shows in the remaining case where a send is in flight
     * but hasn't started streaming yet, and Send is only ever the fallback "else" case. */
    val showStopButton: Boolean get() = isStreaming
    val showSendingSpinner: Boolean get() = !isStreaming && isSending

    /** Matches the pre-existing `IconButton(onClick = onSend, enabled = text.isNotBlank())`,
     * which only ever rendered in that same "else" case above. */
    val canSend: Boolean get() = !isStreaming && !isSending && text.isNotBlank()

    val isProfileSelectorLoading: Boolean get() = isSwitchingProfile
    val isModelSelectorLoading: Boolean get() = isUpdatingComposerConfiguration

    /** True while any single composer-affecting operation is in flight. Not consumed by any UI
     * yet in this phase -- exposed for later phases (attachments, slash commands) to gate against
     * without re-deriving this list of flags themselves. */
    val isComposerBusy: Boolean
        get() = isSending || isStreaming || isSwitchingProfile || isUpdatingComposerConfiguration

    companion object {
        fun from(uiState: ChatUiState): ChatComposerState = ChatComposerState(
            text = uiState.composerText,
            isSending = uiState.isSending,
            isStreaming = uiState.isStreaming,
            isSwitchingProfile = uiState.isSwitchingProfile,
            isUpdatingComposerConfiguration = uiState.isUpdatingComposerConfiguration,
        )
    }
}
