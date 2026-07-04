package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatStartRequest(
    val sessionId: String,
    val message: String,
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val profile: String? = null,
    /** Only ever sent as `true` or omitted, never explicit `false` -- matches iOS's
     * `explicitModelPick ? true : nil`. */
    val explicitModelPick: Boolean? = null,
    /** Genuinely consumed server-side (not just display metadata) -- see [MessageAttachment] and
     * V5 Phase 5 recon. Caller should apply [capAtChatStartLimit] before assigning this so nothing
     * is silently dropped without the app being able to detect it. Null/empty is omitted from the
     * wire body (`HermexJson.explicitNulls = false`), matching every other optional field here. */
    val attachments: List<MessageAttachment>? = null,
)

@Serializable
data class ChatStartResponse(
    val streamId: String? = null,
    val sessionId: String? = null,
    val error: String? = null,
)

@Serializable
data class ChatCancelResponse(
    val ok: Boolean? = null,
    val cancelled: Boolean? = null,
    val streamId: String? = null,
    val error: String? = null,
)
