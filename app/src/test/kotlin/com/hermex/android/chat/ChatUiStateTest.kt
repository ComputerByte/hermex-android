package com.hermex.android.chat

import com.hermex.android.core.network.dto.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatUiStateTest {
    @Test
    fun `toHistoricalToolCallUi maps a persisted tool-role message into a ToolCallUi`() {
        val message = ChatMessage(
            role = "tool",
            name = "Bash",
            toolCallId = "call_123",
            content = "{\"exit_code\":0,\"stdout\":\"ok\"}",
        )

        val toolCall = message.toHistoricalToolCallUi()

        assertEquals("call_123", toolCall?.stableId)
        assertEquals("Bash", toolCall?.name)
        assertEquals("{\"exit_code\":0,\"stdout\":\"ok\"}", toolCall?.preview)
        assertEquals(true, toolCall?.isComplete)
        assertEquals(false, toolCall?.isError)
    }

    @Test
    fun `toHistoricalToolCallUi falls back to the message's stableId when toolCallId is missing`() {
        val message = ChatMessage(role = "tool", name = "Bash", toolCallId = null, content = "output")

        val toolCall = message.toHistoricalToolCallUi()

        assertEquals(message.stableId, toolCall?.stableId)
    }

    @Test
    fun `toHistoricalToolCallUi returns null for non-tool roles so callers fall back to MessageBubble`() {
        assertNull(ChatMessage(role = "assistant", content = "Hello").toHistoricalToolCallUi())
        assertNull(ChatMessage(role = "user", content = "Hi").toHistoricalToolCallUi())
        assertNull(ChatMessage(role = null, content = "Hi").toHistoricalToolCallUi())
    }
}
