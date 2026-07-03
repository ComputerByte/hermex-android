package com.hermex.android.core.network.dto

import com.hermex.android.core.util.LossyBooleanSerializer
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class CronJobsResponse(
    @Serializable(with = CronJobListSerializer::class) val jobs: List<CronJob>? = null,
)

/** Read-only view of a scheduled job -- this app never creates/edits crons (deliberately out of
 * MVP scope, see API_CONTRACT.md), so unlike the iOS model this collapses `schedule` straight to
 * its display text rather than keeping the full editable sub-object, and status derivation skips
 * the "needsAttention" edge case (which depended on `repeat`/schedule-kind fields only relevant
 * to editing) in favor of the simpler active/paused/off/error states, which is all a read-only
 * list needs. */
@Serializable
data class CronJob(
    val jobId: String? = null,
    val name: String? = null,
    val prompt: String? = null,
    @Serializable(with = ScheduleTextSerializer::class) val schedule: String? = null,
    val scheduleDisplay: String? = null,
    @Serializable(with = LossyBooleanSerializer::class) val enabled: Boolean? = null,
    val state: String? = null,
    @Serializable(with = FlexibleDateSerializer::class) val nextRunAt: Double? = null,
    @Serializable(with = FlexibleDateSerializer::class) val lastRunAt: Double? = null,
    val lastStatus: String? = null,
    val lastError: String? = null,
    val lastDeliveryError: String? = null,
    val deliver: String? = null,
    val skills: List<String>? = null,
    val model: String? = null,
    val profile: String? = null,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: effectiveScheduleText?.takeIf { it.isNotBlank() } ?: "Untitled Task"

    val effectiveScheduleText: String?
        get() = scheduleDisplay ?: schedule

    val status: CronJobStatus
        get() = when {
            state == "paused" -> CronJobStatus.PAUSED
            enabled == false -> CronJobStatus.OFF
            lastStatus == "error" -> CronJobStatus.ERROR
            else -> CronJobStatus.ACTIVE
        }
}

enum class CronJobStatus { ACTIVE, PAUSED, OFF, ERROR }

/** The server sends the job identifier as plain `id`, not `job_id` -- iOS's Cron.swift decode
 * tries `id` first, falling back to `jobId` only if `id` is absent. kotlinx's per-field
 * serializers can't see a sibling key, so this rewrites the JSON object before the generated
 * decoder runs, mirroring that same precedence. Without it `jobId` decodes to null for real
 * server payloads and every row silently becomes untappable. */
object CronJobSerializer : JsonTransformingSerializer<CronJob>(CronJob.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = rewriteJobId(element)
}

/** Same `id` -> `job_id` rewrite as [CronJobSerializer], applied element-wise for the `jobs`
 * array response -- a `List<CronJob>` field can't reuse [CronJobSerializer] directly since that
 * targets a single object, not an array. */
object CronJobListSerializer : JsonTransformingSerializer<List<CronJob>>(ListSerializer(CronJob.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val array = element as? JsonArray ?: return element
        return JsonArray(array.map { rewriteJobId(it) })
    }
}

private fun rewriteJobId(element: JsonElement): JsonElement {
    val obj = element as? JsonObject ?: return element
    val id = (obj["id"] as? JsonPrimitive)?.takeIf { it.isString || it.content.isNotBlank() } ?: return obj
    return JsonObject(obj + ("job_id" to id))
}

/** Body for run/pause/resume/delete -- all four share this exact shape server-side. */
@Serializable
data class CronJobIdRequest(
    val jobId: String,
    val reason: String? = null,
)

@Serializable
data class CronMutationResponse(
    val ok: Boolean? = null,
    @Serializable(with = CronJobSerializer::class) val job: CronJob? = null,
    val error: String? = null,
)

@Serializable
data class CronStatusResponse(
    val jobId: String? = null,
    @Serializable(with = LossyBooleanSerializer::class) val running: Boolean? = null,
    val elapsed: Double? = null,
    val error: String? = null,
)

@Serializable
data class CronOutputResponse(
    val jobId: String? = null,
    val outputs: List<CronOutputItem>? = null,
)

@Serializable
data class CronOutputItem(
    val filename: String? = null,
    val content: String? = null,
)

/** `schedule` arrives as either a bare string or an object with `kind`/`expression`/`expr`/
 * `run_at`/`every` fields -- collapse to the first meaningful display string either way. */
object ScheduleTextSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ScheduleText", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return textFrom(jsonDecoder.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: String?) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(if (value == null) JsonNull else JsonPrimitive(value))
    }

    private fun textFrom(element: JsonElement): String? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> element.content.takeIf { element.isString || it.isNotBlank() }
        is JsonObject -> {
            val fields = listOf("expression", "expr", "run_at", "every", "kind")
            fields.firstNotNullOfOrNull { key -> (element[key] as? JsonPrimitive)?.takeIf { it.isString }?.content }
        }
        else -> null
    }
}

/** `next_run_at`/`last_run_at` arrive as a raw epoch number, a numeric string, or an ISO-8601
 * string (with or without fractional seconds) -- normalize all three to epoch seconds so the
 * rest of the app only ever deals with a plain Double, matching every other timestamp field. */
object FlexibleDateSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleDate", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return null
        primitive.doubleOrNull?.let { return it }
        val content = primitive.content.trim()
        content.toDoubleOrNull()?.let { return it }
        return try {
            Instant.parse(content).toEpochMilli() / 1000.0
        } catch (e: DateTimeParseException) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(if (value == null) JsonNull else JsonPrimitive(value))
    }
}
