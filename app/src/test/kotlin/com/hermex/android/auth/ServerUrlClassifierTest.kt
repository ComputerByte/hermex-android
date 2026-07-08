package com.hermex.android.auth

import org.junit.Assert.*
import org.junit.Test

class ServerUrlClassifierTest {
    @Test
    fun https_isAllowed() {
        assertTrue(classify("https://hermes.example.com") is ServerUrlPolicy.Allowed)
        assertTrue(classify("https://192.168.1.100") is ServerUrlPolicy.Allowed)
        assertTrue(classify("https://localhost") is ServerUrlPolicy.Allowed)
    }

    @Test
    fun http_localhost_isLocalHttp() {
        assertTrue(classify("http://localhost") is ServerUrlPolicy.LocalHttp)
        assertTrue(classify("http://127.0.0.1") is ServerUrlPolicy.LocalHttp)
    }

    @Test
    fun http_privateRanges_isLocalHttp() {
        // 10.0.0.0/8
        assertTrue(classify("http://10.0.0.1") is ServerUrlPolicy.LocalHttp)
        assertTrue(classify("http://10.255.255.255") is ServerUrlPolicy.LocalHttp)
        assertTrue(classify("http://10.1.2.3") is ServerUrlPolicy.LocalHttp)

        // 172.16.0.0/12
        assertTrue(classify("http://172.16.0.1") is ServerUrlPolicy.LocalHttp)
        assertTrue(classify("http://172.31.255.255") is ServerUrlPolicy.LocalHttp)
        assertTrue(classify("http://172.16.1.2") is ServerUrlPolicy.LocalHttp)

        // 192.168.0.0/16
        assertTrue(classify("http://192.168.0.1") is ServerUrlPolicy.LocalHttp)
        assertTrue(classify("http://192.168.255.255") is ServerUrlPolicy.LocalHttp)
        assertTrue(classify("http://192.168.1.100") is ServerUrlPolicy.LocalHttp)

        // 100.64.0.0/10 (CGNAT/Tailscale)
        assertTrue(classify("http://100.64.0.1") is ServerUrlPolicy.LocalHttp)
        assertTrue(classify("http://100.127.255.255") is ServerUrlPolicy.LocalHttp)
    }

    @Test
    fun http_mdnsLocal_isLocalHttp() {
        assertTrue(classify("http://hermes.local") is ServerUrlPolicy.LocalHttp)
        assertTrue(classify("http://webui.local") is ServerUrlPolicy.LocalHttp)
    }

    @Test
    fun http_public_isPublicHttp() {
        assertTrue(classify("http://google.com") is ServerUrlPolicy.PublicHttp)
        assertTrue(classify("http://example.com") is ServerUrlPolicy.PublicHttp)
        assertTrue(classify("http://1.2.3.4") is ServerUrlPolicy.PublicHttp)
    }

    @Test
    fun http_publicIPRanges_isPublicHttp() {
        // 8.8.8.8 (Google DNS) - public
        assertTrue(classify("http://8.8.8.8") is ServerUrlPolicy.PublicHttp)
        // 1.1.1.1 (Cloudflare) - public
        assertTrue(classify("http://1.1.1.1") is ServerUrlPolicy.PublicHttp)
    }

    @Test
    fun bareHostname_defaultsToHttps_allowed() {
        // No scheme = defaults to https, which is always allowed
        assertTrue(classify("hermes.example.com") is ServerUrlPolicy.Allowed)
    }

    private fun classify(url: String) = ServerUrlClassifier.classify(url)
}