package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/git/status?session_id=...` -- Git status for a session's workspace.
 */
@Serializable
data class GitStatusResponse(
    val is_git: Boolean? = null,
    val branch: String? = null,
    val commit: String? = null,
    val totals: GitTotals? = null,
    val files: List<GitFileStatus>? = null,
    val error: String? = null,
)

/**
 * Backend wraps `git_status()` result under a `"git"` key:
 * `{"git": {is_git: true, branch: "main", ...}}`.
 * Errors come as `{"error": "..."}` at the top level.
 */
@Serializable
data class GitStatusWrapper(
    val git: GitStatusResponse? = null,
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
    val status: String? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
)

/**
 * `GET /api/git/diff?session_id=...&path=...&kind=...`
 * Backend wraps under `"diff"` key: `{"diff": {diff: "...", binary: false, ...}}`.
 */
@Serializable
data class GitDiffResponse(
    val diff: String? = null,
    val binary: Boolean? = null,
    val size: Int? = null,
    val error: String? = null,
)

@Serializable
data class GitDiffWrapper(
    val diff: GitDiffResponse? = null,
    val error: String? = null,
)

/**
 * `GET /api/git/branches?session_id=...`
 * Backend wraps under `"branches"` key.
 */
@Serializable
data class GitBranchesResponse(
    val current: String? = null,
    val branches: List<GitBranch>? = null,
    val error: String? = null,
)

@Serializable
data class GitBranchesWrapper(
    val branches: GitBranchesResponse? = null,
    val error: String? = null,
)

@Serializable
data class GitBranch(
    val name: String? = null,
    val current: Boolean? = null,
    val ahead: Int? = null,
    val behind: Int? = null,
)
