package com.hermex.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseEventParserTest {
    @Test
    fun `token event extracts text`() {
        val event = SseEventParser.parse("token", """{"text":"hello"}""")
        assertEquals(SseEvent.Token("hello"), event)
    }

    @Test
    fun `reasoning event extracts text`() {
        val event = SseEventParser.parse("reasoning", """{"text":"thinking..."}""")
        assertEquals(SseEvent.Reasoning("thinking..."), event)
    }

    @Test
    fun `token with missing text field yields empty string, not a crash`() {
        val event = SseEventParser.parse("token", """{}""")
        assertEquals(SseEvent.Token(""), event)
    }

    @Test
    fun `tool event with tid present uses tid over id`() {
        val event = SseEventParser.parse(
            "tool",
            """{"event_type":"tool.started","name":"read_file","tid":"tid-1","id":"id-1"}""",
        )
        assertTrue(event is SseEvent.ToolStarted)
        assertEquals("tid-1", (event as SseEvent.ToolStarted).payload.stableId)
    }

    @Test
    fun `tool event with only call_id present falls back to call_id`() {
        val event = SseEventParser.parse(
            "tool",
            """{"event_type":"tool.started","name":"shell","call_id":"call-1"}""",
        )
        assertTrue(event is SseEvent.ToolStarted)
        assertEquals("call-1", (event as SseEvent.ToolStarted).payload.stableId)
    }

    @Test
    fun `tool id precedence order is tid, id, tool_call_id, tool_use_id, call_id`() {
        // id beats tool_call_id when tid is absent.
        val event1 = SseEventParser.parse("tool", """{"id":"id-1","tool_call_id":"tcid-1","call_id":"cid-1"}""")
        assertEquals("id-1", (event1 as SseEvent.ToolStarted).payload.stableId)

        // tool_call_id beats tool_use_id and call_id when tid/id are absent.
        val event2 = SseEventParser.parse("tool", """{"tool_call_id":"tcid-1","tool_use_id":"tuid-1","call_id":"cid-1"}""")
        assertEquals("tcid-1", (event2 as SseEvent.ToolStarted).payload.stableId)

        // tool_use_id beats call_id when only those two are present.
        val event3 = SseEventParser.parse("tool", """{"tool_use_id":"tuid-1","call_id":"cid-1"}""")
        assertEquals("tuid-1", (event3 as SseEvent.ToolStarted).payload.stableId)
    }

    @Test
    fun `tool event with a blank tid falls through to the next id field`() {
        val event = SseEventParser.parse("tool", """{"tid":"","id":"id-1"}""")
        assertEquals("id-1", (event as SseEvent.ToolStarted).payload.stableId)
    }

    @Test
    fun `tool event with no id fields at all has a null stableId, not a crash`() {
        val event = SseEventParser.parse("tool", """{"name":"mystery_tool"}""")
        assertTrue(event is SseEvent.ToolStarted)
        assertNull((event as SseEvent.ToolStarted).payload.stableId)
    }

    @Test
    fun `tool_complete carries duration and is_error`() {
        val event = SseEventParser.parse(
            "tool_complete",
            """{"tid":"tid-1","duration":1.25,"is_error":true}""",
        )
        assertTrue(event is SseEvent.ToolCompleted)
        val payload = (event as SseEvent.ToolCompleted).payload
        assertEquals(1.25, payload.duration)
        assertEquals(true, payload.isError)
    }

    @Test
    fun `tool_complete is_error as numeric 1 is coerced to true via LossyBooleanSerializer`() {
        val event = SseEventParser.parse("tool_complete", """{"tid":"tid-1","is_error":1}""")
        assertEquals(true, (event as SseEvent.ToolCompleted).payload.isError)
    }

    @Test
    fun `done event with an empty payload does not crash`() {
        val event = SseEventParser.parse("done", "")
        assertEquals(SseEvent.Done(null, null), event)
    }

    @Test
    fun `done event decodes an embedded session through the same tolerant SessionDetail model`() {
        val event = SseEventParser.parse("done", """{"session":{"session_id":"abc","title":"Chat"}}""")
        assertTrue(event is SseEvent.Done)
        assertEquals("abc", (event as SseEvent.Done).session?.sessionId)
    }

    @Test
    fun `stream_end with an empty payload is a terminal event, no crash`() {
        assertEquals(SseEvent.StreamEnd, SseEventParser.parse("stream_end", "{}"))
        assertEquals(SseEvent.StreamEnd, SseEventParser.parse("stream_end", ""))
    }

    @Test
    fun `cancel with an empty payload is a terminal event, no crash`() {
        assertEquals(SseEvent.Cancelled, SseEventParser.parse("cancel", "{}"))
        assertEquals(SseEvent.Cancelled, SseEventParser.parse("cancel", ""))
    }

    @Test
    fun `error event prefers the error field over message`() {
        val event = SseEventParser.parse("error", """{"error":"boom","message":"fallback"}""")
        assertEquals(SseEvent.Error("boom"), event)
    }

    @Test
    fun `error event falls back to message when error is absent`() {
        val event = SseEventParser.parse("error", """{"message":"fallback"}""")
        assertEquals(SseEvent.Error("fallback"), event)
    }

    @Test
    fun `unrecognized event name yields Unknown, not a crash`() {
        assertEquals(SseEvent.Unknown, SseEventParser.parse("interim_assistant", """{"text":"hi"}"""))
        assertEquals(SseEvent.Unknown, SseEventParser.parse("approval", """{"approval_id":"1"}"""))
        assertEquals(SseEvent.Unknown, SseEventParser.parse("some_future_event_type", "{}"))
    }

    @Test
    fun `malformed JSON for a recognized event yields Unknown, not a crash`() {
        assertEquals(SseEvent.Unknown, SseEventParser.parse("token", "not json at all"))
        assertEquals(SseEvent.Unknown, SseEventParser.parse("tool", "{unterminated"))
    }
}
