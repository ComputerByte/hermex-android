package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

/** `POST /api/file/save` request body. */
@Serializable
data class FileSaveRequest(
    val session_id: String,
    val path: String,
    val content: String,
)

/** `POST /api/file/save` response. */
@Serializable
data class FileSaveResponse(
    val ok: Boolean? = null,
    val error: String? = null,
)

/**
 * `GET /api/list?session_id=...&path=...` -- directory listing, scoped to a session's workspace
 * (there is no session-independent directory browse). Read-only: no create/rename/delete/upload
 * endpoints exist for this MVP slice.
 */
@Serializable
data class DirectoryListResponse(
    val entries: List<WorkspaceEntry>? = null,
    val path: String? = null,
    val workspace: String? = null,
    val error: String? = null,
)

@Serializable
data class WorkspaceEntry(
    val name: String? = null,
    val path: String? = null,
    /** e.g. `"dir"`/`"file"` -- some servers may only send [isDirectory] instead, or neither. */
    val type: String? = null,
    val size: Long? = null,
    /** Unix seconds. */
    val modified: Double? = null,
    val isDirectory: Boolean? = null,
)

/**
 * `GET /api/file?session_id=...&path=...` -- reads a file as text. Fields through [error] mirror
 * the verified iOS `FileResponse` contract (`HermesMobile/Models/Workspace.swift`). [truncated]
 * and [binary] are NOT verified against a live server -- no confirmed example of either field, or
 * of what this endpoint returns for a non-text file, was found in the iOS reference client or
 * `CONTRACT_TESTS.md`. They're included as a defensive, tolerant-decoding guess (harmless no-op
 * if the server never sends them) so large/binary handling has somewhere to read a signal from if
 * the server adds one later -- see the Phase 1 recon report for the assumption this rests on.
 */
@Serializable
data class FileResponse(
    val content: String? = null,
    val path: String? = null,
    val name: String? = null,
    val language: String? = null,
    val size: Long? = null,
    val lines: Int? = null,
    val truncated: Boolean? = null,
    val binary: Boolean? = null,
    val error: String? = null,
)
