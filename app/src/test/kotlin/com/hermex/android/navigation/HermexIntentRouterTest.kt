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
    fun `hermes-agent session deep link opens session detail`() {
        assertEquals(HermexIntentDestination.Session("s1"), hermexDeepLinkDestination("hermes-agent://session/s1"))
    }

    @Test
    fun `hermes-agent sessions deep link opens session list`() {
        assertEquals(HermexIntentDestination.Sessions, hermexDeepLinkDestination("hermes-agent://sessions"))
    }

    @Test
    fun `hermes-agent task deep link opens task detail`() {
        assertEquals(HermexIntentDestination.Task("nightly digest"), hermexDeepLinkDestination("hermes-agent://task/nightly%20digest"))
    }

    @Test
    fun `query based session id works on hermex scheme`() {
        assertEquals(HermexIntentDestination.Session("abc123"), hermexDeepLinkDestination("hermex://session?id=abc123"))
    }

    @Test
    fun `query based session_id works on hermes-agent scheme`() {
        assertEquals(HermexIntentDestination.Session("xyz"), hermexDeepLinkDestination("hermes-agent://session?session_id=xyz"))
    }

    @Test
    fun `query based session with blank id recovers to sessions`() {
        assertEquals(HermexIntentDestination.Sessions, hermexDeepLinkDestination("hermex://session?id="))
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
    fun `share content with text stages it without sending`() {
        val result = shareContentDestination(" Review before sending ", null)
        assertEquals(HermexIntentDestination.ShareContent(text = "Review before sending", uri = null), result)
    }

    @Test
    fun `blank share text is ignored`() {
        assertNull(shareContentDestination("   ", null))
    }

    @Test
    fun `null share text and null uri is ignored`() {
        assertNull(shareContentDestination(null, null))
    }
}
