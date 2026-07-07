package com.hermex.android.sessions

import com.hermex.android.core.network.dto.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionListUiStateTest {
    private fun state(sessions: List<SessionSummary>, showSubagentSessions: Boolean) =
        SessionListUiState(isLoading = false, sessions = sessions, showSubagentSessions = showSubagentSessions)

    @Test
    fun `hiding subagent sessions removes sessions with a parentSessionId, not just sourceTag subagent`() {
        // Mirrors the real shape reported by testers: a session nested under a parent (surfaced
        // via parentSessionId, per Issue #5's grouping) whose sourceTag is unrelated/absent --
        // the pre-fix filter only checked sourceTag, so this row survived filtering and still
        // rendered indented under its parent even with the toggle off.
        val parent = SessionSummary(sessionId = "p1", title = "Parent")
        val child = SessionSummary(sessionId = "c1", title = "Child", parentSessionId = "p1", sourceTag = null)
        val sessions = listOf(parent, child)

        val shown = state(sessions, showSubagentSessions = true).filteredSessions
        assertEquals(listOf(parent, child), shown)

        val hidden = state(sessions, showSubagentSessions = false).filteredSessions
        assertEquals(listOf(parent), hidden)
    }

    @Test
    fun `hiding subagent sessions still removes sessions tagged via sourceTag`() {
        val normal = SessionSummary(sessionId = "s1", title = "Normal")
        val tagged = SessionSummary(sessionId = "s2", title = "Tagged", sourceTag = "subagent")
        val sessions = listOf(normal, tagged)

        val hidden = state(sessions, showSubagentSessions = false).filteredSessions
        assertEquals(listOf(normal), hidden)
    }

    @Test
    fun `groupedSessions never nests a child once its parent has been filtered out`() {
        val parent = SessionSummary(sessionId = "p1", title = "Parent")
        val child = SessionSummary(sessionId = "c1", title = "Child", parentSessionId = "p1")
        val sessions = listOf(parent, child)

        val groups = state(sessions, showSubagentSessions = false).groupedSessions
        assertTrue(groups.all { it.children.isEmpty() })
    }
}
