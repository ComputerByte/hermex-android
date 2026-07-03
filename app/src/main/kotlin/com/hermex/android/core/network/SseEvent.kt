package com.hermex.android.core.network

import com.hermex.android.core.network.dto.SessionDetail
import kotlinx.serialization.json.JsonElement

sealed interface SseEvent {
    data class Token(val text: String) : SseEvent
    data class Reasoning(val text: String) : SseEvent
    data class ToolStarted(val payload: ToolEventPayload) : SseEvent
    data class ToolCompleted(val payload: ToolEventPayload) : SseEvent
    data class Done(val session: SessionDetail?, val usage: JsonElement?) : SseEvent
    data object StreamEnd : SseEvent
    data object Cancelled : SseEvent
    data class Error(val message: String) : SseEvent

    /** A connection-level failure (network drop, non-2xx on connect) -- distinct from a
     * server-sent `error` event, which is [Error]. */
    data class TransportError(val message: String) : SseEvent

    /** Any unrecognized event name. The server may add new event types at any time; this must
     * never crash the parser. */
    data object Unknown : SseEvent
}

data class ToolEventPayload(
    val eventType: String?,
    val name: String?,
    val preview: String?,
    val args: JsonElement?,
    val duration: Double?,
    val isError: Boolean?,
    /** First non-blank of `tid`, `id`, `tool_call_id`, `tool_use_id`, `call_id`, in that order. */
    val stableId: String?,
)
