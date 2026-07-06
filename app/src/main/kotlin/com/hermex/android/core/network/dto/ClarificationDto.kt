package com.hermex.android.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClarificationPendingResponse(
    val pending: PendingClarificationDto? = null,
    @SerialName("pending_count") val pendingCount: Int? = null,
)

@Serializable
data class PendingClarificationDto(
    @SerialName("clarify_id") val clarifyId: String? = null,
    val question: String? = null,
    @SerialName("choices_offered") val choicesOffered: List<String>? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val kind: String? = null,
    @SerialName("requested_at") val requestedAt: Double? = null,
    @SerialName("timeout_seconds") val timeoutSeconds: Int? = null,
    @SerialName("expires_at") val expiresAt: Double? = null,
)

@Serializable
data class ClarificationRespondRequest(
    @SerialName("session_id") val sessionId: String,
    val response: String,
    @SerialName("clarify_id") val clarifyId: String? = null,
)

@Serializable
data class ClarificationRespondResponse(
    val ok: Boolean? = null,
    val response: String? = null,
    val error: String? = null,
    val stale: Boolean? = null,
)
