package com.hermex.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProjectsResponse(
    val projects: List<ProjectSummary>? = null,
)

@Serializable
data class ProjectSummary(
    val projectId: String? = null,
    val name: String? = null,
    /** Hex color string, e.g. "#7cb9ff" -- one of [ProjectColorPalette], though the server
     * doesn't validate this, so any string decodes fine. */
    val color: String? = null,
    val createdAt: Double? = null,
) {
    val displayName: String
        get() = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled Project"
}

@Serializable
data class ProjectMutationResponse(
    val ok: Boolean? = null,
    val project: ProjectSummary? = null,
    val error: String? = null,
)

@Serializable
data class CreateProjectRequest(
    val name: String,
    val color: String? = null,
)

@Serializable
data class RenameProjectRequest(
    val projectId: String,
    val name: String,
    val color: String? = null,
)

@Serializable
data class ProjectIdRequest(
    val projectId: String,
)

/** The 8 colors iOS's project creation sheet offers -- matched here so a project created on
 * either client looks the same on both. */
object ProjectColorPalette {
    val colors = listOf(
        "#7cb9ff", // Sky
        "#f5c542", // Gold
        "#e94560", // Red
        "#50c878", // Green
        "#c084fc", // Violet
        "#fb923c", // Orange
        "#67e8f9", // Cyan
        "#f472b6", // Pink
    )
}
