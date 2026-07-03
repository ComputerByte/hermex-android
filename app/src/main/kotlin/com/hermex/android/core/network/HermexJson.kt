package com.hermex.android.core.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Single shared Json config for the whole app. Tolerant decoding is a hard requirement -- the
 * server may add, rename, or omit fields at any time -- mirroring the iOS client's blanket use
 * of optionals plus `JSONDecoder.KeyDecodingStrategy.convertFromSnakeCase`.
 *
 * `isLenient` + `coerceInputValues` cover most string<->number type drift for free (verified in
 * [JsonCoercionBehaviorTest]), which is why most DTO fields below are plain nullable types with
 * no custom serializer. The two documented exceptions are [com.hermex.android.core.util.LossyBooleanSerializer]
 * (numeric 0/1 into a `Boolean` field, which lenient mode does NOT cover) and
 * `ChatMessage.content`'s structural string-or-array handling.
 */
@OptIn(ExperimentalSerializationApi::class)
val HermexJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
}
