package com.hermex.android.auth

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class InvalidServerUrlException : Exception("Enter a valid server URL.")

/**
 * Ported near-verbatim from the iOS client's `AuthManager.normalizedServerURL` (see
 * HermesMobile/Auth/AuthManager.swift in the iOS repo) so both clients accept and reject the
 * exact same set of server URL strings.
 */
object ServerUrlNormalizer {
    /** Returns a canonical "scheme://host[:port]/" string, or throws [InvalidServerUrlException]. */
    fun normalize(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) throw InvalidServerUrlException()

        val valueWithScheme = if (trimmed.contains("://")) trimmed else "${defaultScheme(trimmed)}://$trimmed"
        val parsed = valueWithScheme.toHttpUrlOrNull() ?: throw InvalidServerUrlException()
        if (parsed.scheme != "https" && parsed.scheme != "http") throw InvalidServerUrlException()

        return parsed.newBuilder()
            .host(normalizeHost(parsed.host))
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }

    /**
     * Strips a redundant "www." prefix specifically when it precedes "webui." (the app's
     * canonical Cloudflare Tunnel hostname convention, e.g. "webui.example.com" per
     * PROJECT_SPEC), so a user who mistakenly pastes "www.webui.example.com" still connects.
     */
    private fun normalizeHost(host: String): String {
        val lowercased = host.lowercase()
        if (!lowercased.startsWith("www.webui.")) return host
        return host.substring(4)
    }

    private fun defaultScheme(rawValue: String): String {
        val host = "http://$rawValue".toHttpUrlOrNull()?.host?.lowercase() ?: return "https"
        return if (shouldDefaultToPlainHttp(host)) "http" else "https"
    }

    /**
     * Loopback and the Tailscale CGNAT range (100.64.0.0/10) are treated as private/trusted
     * networks where plain HTTP is acceptable without an explicit `http://` -- TLS termination
     * happens at a tunnel/proxy for any publicly reachable hostname instead. Any other bare
     * host/IP (e.g. a plain LAN address) still defaults to https, requiring an explicit
     * `http://` override if the user really means plain HTTP.
     */
    private fun shouldDefaultToPlainHttp(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1") return true
        val octets = host.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 100 && octets[1] in 64..127
    }
}
