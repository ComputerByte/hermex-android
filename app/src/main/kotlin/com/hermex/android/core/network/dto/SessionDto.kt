package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

// SessionDetail intentionally repeats SessionSummary's fields flat (not nested) -- this matches
// the server's actual wire shape (see API_CONTRACT.md), which is itself flat.

@Serializable
data class SessionSummary(
    val sessionId: String? = null,
    val title: String? = null,
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val messageCount: Int? = null,
    val createdAt: Double? = null,
    val updatedAt: Double? = null,
    val lastMessageAt: Double? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null,
    val projectId: String? = null,
    val profile: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val estimatedCost: Double? = null,
    val activeStreamId: String? = null,
    val isStreaming: Boolean? = null,
    val isCliSession: Boolean? = null,
    val sourceTag: String? = null,
    val sessionSource: String? = null,
    val sourceLabel: String? = null,
)

@Serializable
data class SessionDetail(
    val sessionId: String? = null,
    val title: String? = null,
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val messageCount: Int? = null,
    val createdAt: Double? = null,
    val updatedAt: Double? = null,
    val lastMessageAt: Double? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null,
    val projectId: String? = null,
    val profile: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val estimatedCost: Double? = null,
    val activeStreamId: String? = null,
    val isStreaming: Boolean? = null,
    val isCliSession: Boolean? = null,
    val sourceTag: String? = null,
    val sessionSource: String? = null,
    val sourceLabel: String? = null,
    val messages: List<ChatMessage>? = null,
    val pendingUserMessage: String? = null,
    val contextLength: Int? = null,
    val thresholdTokens: Int? = null,
    val lastPromptTokens: Int? = null,
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionSummary>? = null,
    val cliCount: Int? = null,
    val serverTime: Double? = null,
    val serverTz: String? = null,
)

@Serializable
data class SessionResponse(
    val session: SessionDetail? = null,
)

@Serializable
data class NewSessionRequest(
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val profile: String? = null,
)
