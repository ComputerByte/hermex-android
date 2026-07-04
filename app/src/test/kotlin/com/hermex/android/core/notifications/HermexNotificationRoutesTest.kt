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
}
