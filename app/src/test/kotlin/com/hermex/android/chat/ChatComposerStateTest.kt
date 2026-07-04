package com.hermex.android.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerStateTest {
    private fun idle(text: String = "") = ChatComposerState(
        text = text,
        isSending = false,
        isStreaming = false,
        isSwitchingProfile = false,
        isUpdatingComposerConfiguration = false,
    )

    @Test
    fun `text field is enabled when idle, disabled while sending or streaming`() {
        assertTrue(idle().isTextFieldEnabled)
        assertFalse(idle().copy(isSending = true).isTextFieldEnabled)
        assertFalse(idle().copy(isStreaming = true).isTextFieldEnabled)
        assertFalse(idle().copy(isSending = true, isStreaming = true).isTextFieldEnabled)
    }

    @Test
    fun `canSend requires non-blank text and an idle composer`() {
        assertFalse(idle(text = "").canSend)
        assertFalse(idle(text = "   ").canSend)
        assertTrue(idle(text = "hello").canSend)
        assertFalse(idle(text = "hello").copy(isSending = true).canSend)
        assertFalse(idle(text = "hello").copy(isStreaming = true).canSend)
    }

    @Test
    fun `showStopButton is true only while streaming`() {
        assertFalse(idle().showStopButton)
        assertTrue(idle().copy(isStreaming = true).showStopButton)
        // Streaming still wins even if isSending is somehow also true (matches the original
        // `when` branch order: isStreaming was checked before isSending).
        assertTrue(idle().copy(isStreaming = true, isSending = true).showStopButton)
    }

    @Test
    fun `showSendingSpinner is true only while sending and not yet streaming`() {
        assertFalse(idle().showSendingSpinner)
        assertTrue(idle().copy(isSending = true).showSendingSpinner)
        // Streaming takes priority, matching the original branch order.
        assertFalse(idle().copy(isSending = true, isStreaming = true).showSendingSpinner)
    }

    @Test
    fun `profile and model selector loading flags mirror their source booleans directly`() {
        assertFalse(idle().isProfileSelectorLoading)
        assertTrue(idle().copy(isSwitchingProfile = true).isProfileSelectorLoading)
        assertFalse(idle().isModelSelectorLoading)
        assertTrue(idle().copy(isUpdatingComposerConfiguration = true).isModelSelectorLoading)
    }

    @Test
    fun `isComposerBusy is true if any single contributing flag is true, false when all are false`() {
        assertFalse(idle().isComposerBusy)
        assertTrue(idle().copy(isSending = true).isComposerBusy)
        assertTrue(idle().copy(isStreaming = true).isComposerBusy)
        assertTrue(idle().copy(isSwitchingProfile = true).isComposerBusy)
        assertTrue(idle().copy(isUpdatingComposerConfiguration = true).isComposerBusy)
    }

    @Test
    fun `from maps every relevant ChatUiState field verbatim`() {
        val uiState = ChatUiState(
            composerText = "draft",
            isSending = true,
            isStreaming = false,
            isSwitchingProfile = true,
            isUpdatingComposerConfiguration = false,
        )

        val composerState = ChatComposerState.from(uiState)

        assertEquals("draft", composerState.text)
        assertTrue(composerState.isSending)
        assertFalse(composerState.isStreaming)
        assertTrue(composerState.isSwitchingProfile)
        assertFalse(composerState.isUpdatingComposerConfiguration)
    }
}
