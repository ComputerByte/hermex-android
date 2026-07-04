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
    val isUploadingAttachment: Boolean,
) {
    /** Matches the pre-existing `OutlinedTextField(enabled = !isSending && !isStreaming)` check. */
    val isTextFieldEnabled: Boolean get() = !isSending && !isStreaming

    /** Single source of truth for which control occupies the composer's trailing slot -- Stop
     * wins whenever streaming, the sending spinner only shows in the remaining case where a send
     * is in flight but hasn't started streaming yet, and Send is only ever the fallback case.
     * [showStopButton], [showSendingSpinner], and [canSend] all derive from this one `when` so a
     * future state (attachments, slash commands) only has to extend the priority order once,
     * rather than risk it diverging across three independently-written conditions. */
    val trailingAction: TrailingAction
        get() = when {
            isStreaming -> TrailingAction.STOP
            isSending -> TrailingAction.SENDING
            else -> TrailingAction.SEND
        }

    enum class TrailingAction { STOP, SENDING, SEND }

    val showStopButton: Boolean get() = trailingAction == TrailingAction.STOP
    val showSendingSpinner: Boolean get() = trailingAction == TrailingAction.SENDING

    /** Matches the pre-existing `IconButton(onClick = onSend, enabled = text.isNotBlank())`,
     * which only ever rendered in that same fallback case above. */
    val canSend: Boolean get() = trailingAction == TrailingAction.SEND && text.isNotBlank()

    val isProfileSelectorLoading: Boolean get() = isSwitchingProfile
    val isModelSelectorLoading: Boolean get() = isUpdatingComposerConfiguration

    /** True while any single composer-affecting operation is in flight, including uploading an
     * attachment. Not consumed by any UI yet in this phase -- exposed for later phases (slash
     * commands) to gate against without re-deriving this list of flags themselves. */
    val isComposerBusy: Boolean
        get() = isSending || isStreaming || isSwitchingProfile || isUpdatingComposerConfiguration || isUploadingAttachment

    /** Gates the composer's attach button specifically -- disabled while sending, streaming, or
     * already uploading another attachment. Deliberately narrower than [isComposerBusy]: a
     * profile/model switch in progress doesn't need to block attaching a file. */
    val isAttachButtonEnabled: Boolean get() = !isSending && !isStreaming && !isUploadingAttachment

    companion object {
        fun from(uiState: ChatUiState): ChatComposerState = ChatComposerState(
            text = uiState.composerText,
            isSending = uiState.isSending,
            isStreaming = uiState.isStreaming,
            isSwitchingProfile = uiState.isSwitchingProfile,
            isUpdatingComposerConfiguration = uiState.isUpdatingComposerConfiguration,
            isUploadingAttachment = uiState.isUploadingAttachment,
        )
    }
}
