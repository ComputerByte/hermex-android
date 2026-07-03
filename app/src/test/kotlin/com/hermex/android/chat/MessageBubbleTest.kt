package com.hermex.android.chat

import com.hermex.android.core.network.dto.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageBubbleTest {
    @Test
    fun `copyableTextFor returns the message's plain text content`() {
        val message = ChatMessage(role = "assistant", content = "Hello world")
        assertEquals("Hello world", copyableTextFor(message))
    }

    @Test
    fun `copyableTextFor returns an empty string for a message with no content, not a crash`() {
        val message = ChatMessage(role = "tool", content = null)
        assertEquals("", copyableTextFor(message))
    }
}
