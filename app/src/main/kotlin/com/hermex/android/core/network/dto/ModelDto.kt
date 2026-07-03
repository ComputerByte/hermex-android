package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

/** `GET /api/models` -- the model catalog grouped by provider, plus the server's currently
 * remembered default. Mirrors iOS's `ModelsResponse`, though iOS decodes `groups`/`models` as
 * untyped JSON and picks fields out by hand; kotlinx's tolerant decoding lets us use a normal
 * typed structure instead (see [ModelGroupDto]/[ModelOptionDto]) for the same effect. */
@Serializable
data class ModelsResponse(
    val groups: List<ModelGroupDto>? = null,
    val defaultModel: String? = null,
    val activeProvider: String? = null,
)

@Serializable
data class ModelGroupDto(
    val providerId: String? = null,
    val name: String? = null,
    val models: List<ModelOptionDto>? = null,
    val extraModels: List<ModelOptionDto>? = null,
)

@Serializable
data class ModelOptionDto(
    val id: String? = null,
    val name: String? = null,
    val label: String? = null,
    val providerId: String? = null,
)

/** `GET /api/models/live` -- an uncached, real-time model list for the active provider, used to
 * overlay onto the (possibly stale) cached catalog from [ModelsResponse]. Best-effort: a failed
 * or empty live fetch just means the cached catalog is shown as-is. */
@Serializable
data class ModelsLiveResponse(
    val provider: String? = null,
    val models: List<ModelOptionDto>? = null,
    val count: Int? = null,
)

@Serializable
data class DefaultModelRequest(
    val model: String,
)

@Serializable
data class DefaultModelResponse(
    val ok: Boolean? = null,
    val model: String? = null,
)
