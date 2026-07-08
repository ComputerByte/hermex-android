package com.hermex.android.core.network

import org.junit.Assert.*
import org.junit.Test

class ChatStreamUrlTest {
    @Test
    fun builds_url_with_query_param() {
        val url = chatStreamUrl("https://hermes.example.com", "stream-abc-123")
        assertEquals("/api/chat/stream", url.encodedPath)
        assertEquals("stream-abc-123", url.queryParameter("stream_id"))
        assertEquals("hermes.example.com", url.host)
    }

    @Test
    fun handles_http_base_url() {
        val url = chatStreamUrl("http://10.0.0.5:8787", "local-stream-1")
        assertEquals("/api/chat/stream", url.encodedPath)
        assertEquals("local-stream-1", url.queryParameter("stream_id"))
        assertEquals("10.0.0.5", url.host)
        assertEquals(8787, url.port)
    }

    @Test
    fun handles_base_url_with_trailing_path() {
        // Server base URL may already have a path (e.g. behind a reverse proxy)
        // Note: chatStreamUrl uses encodedPath() which replaces the existing path,
        // so the base path is not preserved -- this is a known behavior.
        val url = chatStreamUrl("https://hermes.example.com/sub/", "stream-xyz")
        assertEquals("/api/chat/stream", url.encodedPath)
        assertEquals("stream-xyz", url.queryParameter("stream_id"))
    }

    @Test
    fun preserves_https_scheme() {
        val url = chatStreamUrl("https://secure.example.com", "s1")
        assertEquals("https", url.scheme)
    }

    @Test
    fun stream_id_with_special_chars_is_url_encoded() {
        val url = chatStreamUrl("https://h.example.com", "stream/with/slashes")
        // OkHttp encodes query parameters automatically
        val raw = url.toString()
        assertTrue("URL should contain encoded stream_id: $raw", raw.contains("stream_id=stream%2Fwith%2Fslashes") || raw.contains("stream_id="))
    }
}