package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectDtoTest {
    @Test
    fun `displayName falls back to 'Untitled Project' when name is missing or blank`() {
        assertEquals("Mobile Redesign", ProjectSummary(name = "Mobile Redesign").displayName)
        assertEquals("Untitled Project", ProjectSummary(name = null).displayName)
        assertEquals("Untitled Project", ProjectSummary(name = "   ").displayName)
    }

    @Test
    fun `full projects list response decodes tolerantly`() {
        val response = HermexJson.decodeFromString<ProjectsResponse>(
            """{"projects":[
                {"project_id":"proj-1","name":"Mobile Redesign","color":"#7cb9ff","created_at":1719000000.0},
                {"project_id":"proj-2","name":"Backend Services"}
            ]}""",
        )
        assertEquals(2, response.projects?.size)
        assertEquals("proj-1", response.projects?.get(0)?.projectId)
        assertEquals("#7cb9ff", response.projects?.get(0)?.color)
        assertEquals(1719000000.0, response.projects?.get(0)?.createdAt)
        assertNull(response.projects?.get(1)?.color)
    }

    @Test
    fun `mutation response decodes ok, project, and error tolerantly`() {
        val success = HermexJson.decodeFromString<ProjectMutationResponse>(
            """{"ok":true,"project":{"project_id":"proj-1","name":"Mobile Redesign"}}""",
        )
        assertEquals(true, success.ok)
        assertEquals("proj-1", success.project?.projectId)
        assertNull(success.error)

        val failure = HermexJson.decodeFromString<ProjectMutationResponse>(
            """{"ok":false,"error":"name already in use"}""",
        )
        assertEquals(false, failure.ok)
        assertEquals("name already in use", failure.error)
    }

    @Test
    fun `color palette has 8 distinct hex colors`() {
        assertEquals(8, ProjectColorPalette.colors.size)
        assertEquals(8, ProjectColorPalette.colors.toSet().size)
    }
}
