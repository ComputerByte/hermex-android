package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/git/status?session_id=...` -- Git status for a session's workspace.
 */
@Serializable
data class GitStatusResponse(
    val is_git: Boolean? = null,
    val branch: String? = null,
    /** Short SHA of HEAD commit. */
    val commit: String? = null,
    val totals: GitTotals? = null,
    val files: List<GitFileStatus>? = null,
    val error: String? = null,
)

@Serializable
data class GitTotals(
    val changed: Int? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
)

@Serializable
data class GitFileStatus(
    val path: String? = null,
    /** Single-letter Git status code: M, A, D, R, ?, etc. */
    val status: String? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
)

/**
 * `GET /api/git/diff?session_id=...&path=...&kind=...` -- Git diff for a file.
 */
@Serializable
data class GitDiffResponse(
    val diff: String? = null,
    val binary: Boolean? = null,
    val size: Int? = null,
    val error: String? = null,
)

/**
 * `GET /api/git/branches?session_id=...` -- Git branch list.
 */
@Serializable
data class GitBranchesResponse(
    val current: String? = null,
    val branches: List<GitBranch>? = null,
    val error: String? = null,
)

@Serializable
data class GitBranch(
    val name: String? = null,
    val current: Boolean? = null,
    val ahead: Int? = null,
    val behind: Int? = null,
)
