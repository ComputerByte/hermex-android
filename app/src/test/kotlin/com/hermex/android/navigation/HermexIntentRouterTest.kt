package com.hermex.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HermexIntentRouterTest {
    @Test
    fun `session deep link opens session detail`() {
        assertEquals(HermexIntentDestination.Session("s1"), hermexDeepLinkDestination("hermex://session/s1"))
    }

    @Test
    fun `sessions deep link opens session list`() {
        assertEquals(HermexIntentDestination.Sessions, hermexDeepLinkDestination("hermex://sessions"))
    }

    @Test
    fun `task deep link opens task detail`() {
        assertEquals(HermexIntentDestination.Task("nightly digest"), hermexDeepLinkDestination("hermex://task/nightly%20digest"))
    }

    @Test
    fun `unsupported deep link recovers to sessions`() {
        assertEquals(HermexIntentDestination.Sessions, hermexDeepLinkDestination("hermex://unknown/place"))
    }

    @Test
    fun `malformed session deep link recovers to sessions`() {
        assertEquals(HermexIntentDestination.Sessions, hermexDeepLinkDestination("hermex://session/%ZZ"))
    }

    @Test
    fun `malformed task deep link recovers to tasks`() {
        assertEquals(HermexIntentDestination.Tasks, hermexDeepLinkDestination("hermex://task/%ZZ"))
    }

    @Test
    fun `share intent stages plain text without sending`() {
        assertEquals(HermexIntentDestination.ShareText("Review this before sending"), shareTextDestination(" Review this before sending "))
    }

    @Test
    fun `blank share text is ignored`() {
        assertNull(shareTextDestination("   "))
    }
}
