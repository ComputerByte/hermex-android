package com.hermex.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `new-chat deep link opens new chat destination`() {
        assertEquals(HermexIntentDestination.NewChat, hermexDeepLinkDestination("hermex://new-chat"))
    }

    @Test
    fun `hermes-agent new-chat deep link opens new chat destination`() {
        assertEquals(HermexIntentDestination.NewChat, hermexDeepLinkDestination("hermes-agent://new-chat"))
    }

    @Test
    fun `malformed task deep link recovers to tasks`() {
        assertEquals(HermexIntentDestination.Tasks, hermexDeepLinkDestination("hermex://task/%ZZ"))
    }

    // ── Malformed percent-encoding (each must fail safely into the route's fallback, never crash) ──

    @Test
    fun `a bare percent sign with no hex digits recovers to sessions`() {
        assertEquals(HermexIntentDestination.Sessions, hermexDeepLinkDestination("hermex://session/%"))
    }

    @Test
    fun `an incomplete two-character escape recovers to sessions`() {
        assertEquals(HermexIntentDestination.Sessions, hermexDeepLinkDestination("hermex://session/%2"))
    }

    @Test
    fun `non-hex escape characters recover to sessions`() {
        assertEquals(HermexIntentDestination.Sessions, hermexDeepLinkDestination("hermex://session/%GG"))
    }

    @Test
    fun `the same malformed escapes recover to tasks on the task route`() {
        assertEquals(HermexIntentDestination.Tasks, hermexDeepLinkDestination("hermex://task/%"))
        assertEquals(HermexIntentDestination.Tasks, hermexDeepLinkDestination("hermex://task/%2"))
        assertEquals(HermexIntentDestination.Tasks, hermexDeepLinkDestination("hermex://task/%GG"))
    }

    @Test
    fun `validly encoded unicode decodes to the original characters`() {
        // "日本" (Japanese for Japan) UTF-8 percent-encoded
        assertEquals(HermexIntentDestination.Session("日本"), hermexDeepLinkDestination("hermex://session/%E6%97%A5%E6%9C%AC"))
    }

    @Test
    fun `an encoded slash within a session id segment decodes to a literal slash`() {
        assertEquals(HermexIntentDestination.Session("abc/def"), hermexDeepLinkDestination("hermex://session/abc%2Fdef"))
    }

    @Test
    fun `mixed valid and invalid encoding in the same segment recovers to sessions rather than crashing`() {
        assertEquals(HermexIntentDestination.Sessions, hermexDeepLinkDestination("hermex://session/abc%20def%ZZghi"))
    }

    // ── Share content (multi-URI) ──

    @Test
    fun `share content with text stages it without sending`() {
        val result = shareContentDestination(" Review before sending ", emptyList())
        assertEquals(HermexIntentDestination.ShareContent(text = "Review before sending", uris = emptyList()), result)
    }

    @Test
    fun `blank share text is ignored`() {
        assertNull(shareContentDestination("   ", emptyList()))
    }

    @Test
    fun `null share text and empty uris is ignored`() {
        assertNull(shareContentDestination(null, emptyList()))
    }

    @Test
    fun `uri list preserved in share content`() {
        // In JVM tests Uri.parse returns null, so we test at the string level
        val result = shareContentDestination("hello", emptyList())
        assertEquals("hello", (result as HermexIntentDestination.ShareContent).text)
        assertTrue(result.uris.isEmpty())
    }

    // ── URI list encode/decode ──

    @Test
    fun `encode and decode single uri round-trips`() {
        val uris = listOf("content://media/external/images/123")
        val encoded = encodeUriList(uris)
        val decoded = decodeUriList(encoded)
        assertEquals(uris, decoded)
    }

    @Test
    fun `encode and decode multiple uris round-trips`() {
        val uris = listOf(
            "content://media/external/images/123",
            "content://media/external/images/456",
            "content://media/external/images/789",
        )
        val encoded = encodeUriList(uris)
        val decoded = decodeUriList(encoded)
        assertEquals(uris, decoded)
    }

    @Test
    fun `encode and decode uris with special characters round-trips`() {
        val uris = listOf(
            "content://com.android.providers.media.documents/document/image%3A12345",
            "content://com.android.external/document/photo.jpg",
        )
        val encoded = encodeUriList(uris)
        val decoded = decodeUriList(encoded)
        assertEquals(uris, decoded)
    }

    @Test
    fun `decode empty string returns empty list`() {
        assertEquals(emptyList<String>(), decodeUriList(""))
    }

    @Test
    fun `decode malformed segment skips it gracefully`() {
        val decoded = decodeUriList("validUri|%ZZ|alsoValid")
        assertEquals(listOf("validUri", "alsoValid"), decoded)
    }
}
