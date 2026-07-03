package com.hermex.android.core.network.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
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
data class SkillSummary(
    val name: String? = null,
    val category: String? = null,
    val description: String? = null,
    val path: String? = null,
)

@Serializable
data class SkillsResponse(
    val skills: List<SkillSummary>? = null,
)

@Serializable
data class SkillDetailResponse(
    val name: String? = null,
    val content: String? = null,
    @Serializable(with = LinkedFilesSerializer::class) val linkedFiles: List<String>? = null,
)

/** `linked_files` arrives in whatever shape the server feels like: a flat `{"name": "content"}`
 * map (use the keys), a bare array of filenames, or nested combinations of both. Walk the raw
 * JSON recursively and collect anything that looks like a filename rather than assuming one
 * fixed shape -- mirrors the iOS client's fallback-chain decoding for this same field. */
object LinkedFilesSerializer : KSerializer<List<String>?> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = listSerialDescriptor(String.serializer().descriptor)

    override fun deserialize(decoder: Decoder): List<String>? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val names = extractNames(jsonDecoder.decodeJsonElement()).toSortedSet().toList()
        return names.ifEmpty { null }
    }

    override fun serialize(encoder: Encoder, value: List<String>?) {
        val jsonEncoder = encoder as JsonEncoder
        val element = if (value == null) JsonNull else JsonArray(value.map { JsonPrimitive(it) })
        jsonEncoder.encodeJsonElement(element)
    }

    private fun extractNames(element: JsonElement): List<String> = when (element) {
        is JsonNull -> emptyList()
        is JsonArray -> element.flatMap { extractNames(it) }
        is JsonObject -> element.flatMap { (key, value) ->
            when {
                value is JsonPrimitive && value.isString -> listOf(key)
                else -> extractNames(value)
            }
        }
        is JsonPrimitive -> {
            val trimmed = element.content.trim()
            if (element.isString && trimmed.isNotEmpty()) listOf(trimmed) else emptyList()
        }
    }
}
