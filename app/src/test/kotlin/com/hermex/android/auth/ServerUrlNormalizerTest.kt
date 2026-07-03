package com.hermex.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlNormalizerTest {
    @Test
    fun `bare hostname defaults to https`() {
        assertEquals("https://hermes.example.com/", ServerUrlNormalizer.normalize("hermes.example.com"))
    }

    @Test
    fun `localhost defaults to http`() {
        assertEquals("http://localhost:8787/", ServerUrlNormalizer.normalize("localhost:8787"))
    }

    @Test
    fun `127_0_0_1 defaults to http`() {
        assertEquals("http://127.0.0.1:8787/", ServerUrlNormalizer.normalize("127.0.0.1:8787"))
    }

    @Test
    fun `tailscale CGNAT range 100_64-127_x_x defaults to http`() {
        assertEquals("http://100.64.1.5/", ServerUrlNormalizer.normalize("100.64.1.5"))
        assertEquals("http://100.127.255.255/", ServerUrlNormalizer.normalize("100.127.255.255"))
    }

    @Test
    fun `IP just outside the tailscale range defaults to https, not http`() {
        // 100.128.x.x is outside 100.64.0.0/10 (which covers 100.64.0.0-100.127.255.255).
        assertEquals("https://100.128.1.5/", ServerUrlNormalizer.normalize("100.128.1.5"))
    }

    @Test
    fun `an ordinary private LAN IP still defaults to https, not http`() {
        // Only loopback and the Tailscale CGNAT range get an automatic http default -- a plain
        // 192.168.x.x address needs an explicit http:// override, matching iOS behavior.
        assertEquals("https://192.168.1.5/", ServerUrlNormalizer.normalize("192.168.1.5"))
    }

    @Test
    fun `explicit https scheme is preserved`() {
        assertEquals("https://hermes.example.com/", ServerUrlNormalizer.normalize("https://hermes.example.com"))
    }

    @Test
    fun `explicit http scheme is honored even for a non-local host`() {
        assertEquals("http://insecure.example.com/", ServerUrlNormalizer.normalize("http://insecure.example.com"))
    }

    @Test
    fun `path query and fragment are stripped`() {
        assertEquals(
            "https://hermes.example.com/",
            ServerUrlNormalizer.normalize("https://hermes.example.com/some/path?x=1#frag"),
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("https://hermes.example.com/", ServerUrlNormalizer.normalize("  hermes.example.com  "))
    }

    @Test
    fun `a www webui prefix is stripped down to the webui host`() {
        assertEquals("https://webui.example.com/", ServerUrlNormalizer.normalize("www.webui.example.com"))
    }

    @Test
    fun `a www prefix NOT followed by webui is left alone`() {
        assertEquals("https://www.example.com/", ServerUrlNormalizer.normalize("www.example.com"))
    }

    @Test
    fun `empty string throws InvalidServerUrlException`() {
        assertThrows(InvalidServerUrlException::class.java) { ServerUrlNormalizer.normalize("") }
    }

    @Test
    fun `blank whitespace-only string throws InvalidServerUrlException`() {
        assertThrows(InvalidServerUrlException::class.java) { ServerUrlNormalizer.normalize("   ") }
    }

    @Test
    fun `a host containing a space is not a valid URL`() {
        assertThrows(InvalidServerUrlException::class.java) { ServerUrlNormalizer.normalize("not a url") }
    }

    @Test
    fun `an unsupported scheme is rejected`() {
        assertThrows(InvalidServerUrlException::class.java) { ServerUrlNormalizer.normalize("ftp://hermes.example.com") }
    }

    @Test
    fun `port is preserved`() {
        assertEquals("https://hermes.example.com:9443/", ServerUrlNormalizer.normalize("hermes.example.com:9443"))
    }
}
