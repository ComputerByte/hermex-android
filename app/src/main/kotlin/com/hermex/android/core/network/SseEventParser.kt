package com.hermex.android.core.network

import com.hermex.android.core.network.dto.DonePayloadDto
import com.hermex.android.core.network.dto.ErrorPayloadDto
import com.hermex.android.core.network.dto.TokenPayloadDto
import com.hermex.android.core.network.dto.ToolPayloadDto
import kotlinx.serialization.SerializationException

/**
 * Maps a raw SSE `event: <name>` + `data: <json>` pair into a typed [SseEvent]. Tolerant by
 * design: an unrecognized event name, or a malformed/empty JSON payload for a recognized event,
 * degrades to [SseEvent.Unknown] rather than throwing -- the server may add new event types or
 * send an edge-case payload shape at any time (see API_CONTRACT.md).
 */
object SseEventParser {
    fun parse(eventName: String, data: String): SseEvent = try {
        when (eventName) {
            "token" -> SseEvent.Token(decode<TokenPayloadDto>(data).text.orEmpty())
            "reasoning" -> SseEvent.Reasoning(decode<TokenPayloadDto>(data).text.orEmpty())
            "tool" -> SseEvent.ToolStarted(toToolPayload(decode(data)))
            "tool_complete" -> SseEvent.ToolCompleted(toToolPayload(decode(data)))
            "done" -> decode<DonePayloadDto>(data.ifBlank { "{}" }).let { SseEvent.Done(it.session, it.usage) }
            "stream_end" -> SseEvent.StreamEnd
            "cancel" -> SseEvent.Cancelled
            "error" -> {
                val payload = decode<ErrorPayloadDto>(data.ifBlank { "{}" })
                SseEvent.Error(payload.error ?: payload.message ?: "Unknown error")
            }
            else -> SseEvent.Unknown
        }
    } catch (e: SerializationException) {
        SseEvent.Unknown
    } catch (e: IllegalArgumentException) {
        SseEvent.Unknown
    }

    private inline fun <reified T> decode(data: String): T = HermexJson.decodeFromString(data)

    private fun toToolPayload(dto: ToolPayloadDto): ToolEventPayload {
        val stableId = listOf(dto.tid, dto.id, dto.toolCallId, dto.toolUseId, dto.callId)
            .firstOrNull { !it.isNullOrBlank() }
        return ToolEventPayload(
            eventType = dto.eventType,
            name = dto.name,
            preview = dto.preview,
            args = dto.args,
            duration = dto.duration,
            isError = dto.isError,
            stableId = stableId,
        )
    }
}
