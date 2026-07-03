package com.hermex.android.core.network.dto

import com.hermex.android.core.util.LossyBooleanSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Wire shape for `token` and `reasoning` SSE events: `{"text": "..."}`. */
@Serializable
data class TokenPayloadDto(val text: String? = null)

/** Wire shape shared by `tool` (started) and `tool_complete` events. The tool's stable id may
 * arrive under any of these keys -- see [com.hermex.android.core.network.SseEventParser]. */
@Serializable
data class ToolPayloadDto(
    val eventType: String? = null,
    val name: String? = null,
    val preview: String? = null,
    val args: JsonElement? = null,
    val duration: Double? = null,
    @Serializable(with = LossyBooleanSerializer::class) val isError: Boolean? = null,
    val tid: String? = null,
    val id: String? = null,
    val toolCallId: String? = null,
    val toolUseId: String? = null,
    val callId: String? = null,
)

/** Wire shape for the terminal `done` event: `{"session": {...}, "usage": {...}}`. */
@Serializable
data class DonePayloadDto(
    val session: SessionDetail? = null,
    val usage: JsonElement? = null,
)

/** Wire shape for `error` events: either `error` or `message` (or both) may be present. */
@Serializable
data class ErrorPayloadDto(
    val error: String? = null,
    val message: String? = null,
)
