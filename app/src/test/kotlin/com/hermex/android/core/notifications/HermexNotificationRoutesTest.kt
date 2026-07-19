package com.hermex.android.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class HermexNotificationRoutesTest {
    @Test
    fun `session notification route opens encoded session deep link`() {
        assertEquals("hermex://session/nightly%20digest", HermexNotificationRoutes.session("nightly digest"))
    }

    @Test
    fun `task notification route opens encoded task deep link`() {
        assertEquals("hermex://task/job%2Fwith%3Fchars", HermexNotificationRoutes.task("job/with?chars"))
    }

    @Test
    fun `list notification routes are terse app entry links`() {
        assertEquals("hermex://sessions", HermexNotificationRoutes.sessions())
        assertEquals("hermex://tasks", HermexNotificationRoutes.tasks())
    }

    // ── Path-segment encoding (shared by ChatScreen and SessionListScreen share actions) ──

    @Test
    fun `a hash in the session id is percent-encoded`() {
        assertEquals("hermex://session/before%23after", HermexNotificationRoutes.session("before#after"))
    }

    @Test
    fun `a percent sign in the session id is itself percent-encoded`() {
        assertEquals("hermex://session/100%2525", HermexNotificationRoutes.session("100%25"))
    }

    @Test
    fun `unicode characters in the session id are percent-encoded UTF-8`() {
        assertEquals("hermex://session/caf%C3%A9", HermexNotificationRoutes.session("café"))
    }

    @Test
    fun `an ordinary UUID-like session id passes through unchanged`() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        assertEquals("hermex://session/$uuid", HermexNotificationRoutes.session(uuid))
    }
}
