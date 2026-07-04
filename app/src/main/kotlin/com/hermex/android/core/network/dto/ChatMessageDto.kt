package com.hermex.android.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ChatMessage(
    val role: String? = null,
    @Serializable(with = MessageContentSerializer::class) val content: String? = null,
    @SerialName("_ts") val ts: Double? = null,
    val timestamp: Double? = null,
    val messageId: String? = null,
    val name: String? = null,
    val toolCallId: String? = null,
    val reasoning: String? = null,
    /** See [MessageAttachment]'s doc for the two wire shapes this must decode (full object vs.
     * bare string) -- confirmed against the `hermes-webui` source in V5 Phase 5 recon. */
    val attachments: List<MessageAttachment>? = null,
) {
    /** The server prefers `_ts` over `timestamp` when both are present. */
    val effectiveTimestamp: Double? get() = ts ?: timestamp

    /** A stable key for list diffing/`LazyColumn` item keys: the server's own id when present,
     * else a best-effort synthetic key for locally-created messages (the optimistic user
     * message, the finalized streamed assistant reply) that never had one. */
    val stableId: String
        get() = messageId ?: "$role-${effectiveTimestamp ?: 0}-${content.hashCode()}"
}

/**
 * `content` arrives on the wire as either a plain string, or (for some message shapes) a
 * structured array of parts like `[{"type":"text","text":"..."}]`. For MVP we only need the
 * plain-text case: extract concatenated `text` fields from an array, or null if none are found.
 * This has to be a full custom `KSerializer<String?>` (not a `KSerializer<String>` composed with
 * the usual automatic nullable-wrapping) because it needs to actively decide "no plain text
 * available" for a non-null-but-unparseable shape, not just handle a literal JSON null.
 */
object MessageContentSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MessageContent", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return extractText(jsonDecoder.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: String?) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(if (value == null) JsonNull else JsonPrimitive(value))
    }

    private fun extractText(element: JsonElement): String? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> element.content
        is JsonArray -> element
            .mapNotNull { part -> ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.content }
            .joinToString("")
            .ifEmpty { null }
        else -> null
    }
}
