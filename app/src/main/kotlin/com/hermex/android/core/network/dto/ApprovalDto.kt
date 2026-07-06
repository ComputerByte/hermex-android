package com.hermex.android.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApprovalPendingResponse(
    val pending: PendingApprovalDto? = null,
    @SerialName("pending_count") val pendingCount: Int = 0,
)

@Serializable
data class PendingApprovalDto(
    @SerialName("approval_id") val approvalId: String? = null,
    val command: String? = null,
    val description: String? = null,
    @SerialName("pattern_key") val patternKey: String? = null,
    @SerialName("pattern_keys") val patternKeys: List<String>? = null,
)

@Serializable
data class ApprovalRespondRequest(
    @SerialName("session_id") val sessionId: String,
    val choice: String,
    @SerialName("approval_id") val approvalId: String? = null,
)

@Serializable
data class ApprovalRespondResponse(
    val ok: Boolean? = null,
    val choice: String? = null,
    @SerialName("stale_cleared") val staleCleared: Boolean? = null,
)

@Serializable
data class SessionYoloResponse(
    val ok: Boolean? = null,
    @SerialName("yolo_enabled") val yoloEnabled: Boolean? = null,
)

@Serializable
data class SessionYoloRequest(
    @SerialName("session_id") val sessionId: String,
    val enabled: Boolean,
)
