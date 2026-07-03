package com.hermex.android.core.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The server may send a boolean field as a JSON boolean, a 0/1 number, or a "true"/"false"/
 * "yes"/"no" string, mirroring the iOS client's `decodeLossyBoolIfPresent` helper. Unlike
 * string<->number drift (which the shared [kotlinx.serialization.json.Json] config's
 * `isLenient` + `coerceInputValues` already coerce for free -- verified empirically in
 * [com.hermex.android.core.network.JsonCoercionBehaviorTest]), a numeric 0/1 into a `Boolean`
 * field is NOT covered by lenient mode and throws without this serializer.
 *
 * Apply via `@Serializable(with = LossyBooleanSerializer::class)` on any nullable `Boolean?`
 * field the server might encode this way.
 */
object LossyBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LossyBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val primitive = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive
            ?: return decoder.decodeBoolean()
        primitive.booleanOrNull?.let { return it }
        return when (primitive.contentOrNull?.lowercase()) {
            "1", "true", "yes" -> true
            "0", "false", "no" -> false
            else -> decoder.decodeBoolean() // let it throw with a useful message for a truly unexpected shape
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }
}
