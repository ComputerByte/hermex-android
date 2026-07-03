package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String? = null,
    val sessions: Int? = null,
    val activeStreams: Int? = null,
    val uptimeSeconds: Double? = null,
)
