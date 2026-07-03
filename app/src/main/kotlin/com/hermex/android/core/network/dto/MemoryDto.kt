package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class MemoryResponse(
    val memory: String? = null,
    val user: String? = null,
    val soul: String? = null,
    val memoryPath: String? = null,
    val userPath: String? = null,
    val soulPath: String? = null,
    val memoryMtime: Double? = null,
    val userMtime: Double? = null,
    val soulMtime: Double? = null,
)
