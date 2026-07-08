package com.hermex.android.auth

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Result of classifying a server URL for security policy.
 *
 * - ALLOWED: HTTPS URLs (public or private), or localhost/LAN HTTP
 * - HTTP_LOCAL: HTTP on localhost or private network (allowed with warning)
 * - HTTP_PUBLIC: HTTP on public internet (blocked)
 */
sealed class ServerUrlPolicy {
    /** HTTPS URLs are always allowed. */
    data object Allowed : ServerUrlPolicy()

    /** HTTP on localhost or private network — allowed with warning. */
    data class LocalHttp(val host: String) : ServerUrlPolicy()

    /** HTTP on public internet — not allowed. */
    data class PublicHttp(val host: String) : ServerUrlPolicy()
}

/**
 * Classifies a server URL for the Hermex 1.0 security policy.
 *
 * - HTTPS: always allowed
 * - HTTP localhost/private: allowed with warning
 * - HTTP public: blocked
 *
 * Note: Android network_security_config.xml cannot express CIDR ranges,
 * so this is the app-side enforcement layer.
 */
object ServerUrlClassifier {
    /**
     * Classifies [rawUrl] according to Hermex 1.0 security policy.
     *
     * @param rawUrl The raw server URL (may or may not have scheme)
     * @return [ServerUrlPolicy] indicating whether the URL is allowed
     */
    fun classify(rawUrl: String): ServerUrlPolicy {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ServerUrlPolicy.Allowed // Empty will be caught elsewhere

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val httpUrl = withScheme.toHttpUrlOrNull() ?: return ServerUrlPolicy.Allowed // Invalid URL caught elsewhere

        // HTTPS is always allowed
        if (httpUrl.scheme == "https") return ServerUrlPolicy.Allowed

        // HTTP — classify by host
        if (httpUrl.scheme == "http") {
            val host = httpUrl.host.lowercase()
            return if (isPrivateHttpHost(host)) {
                ServerUrlPolicy.LocalHttp(host)
            } else {
                ServerUrlPolicy.PublicHttp(host)
            }
        }

        return ServerUrlPolicy.Allowed
    }

    /** Returns true if the host is a private/local HTTP target that Hermex allows with warning. */
    private fun isPrivateHttpHost(host: String): Boolean {
        // localhost variants
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "0.0.0.0") {
            return true
        }

        // .local mDNS
        if (host.endsWith(".local")) {
            return true
        }

        // IP-based private ranges
        val octets = host.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size == 4 && octets.all { it in 0..255 }) {
            // 10.0.0.0/8
            if (octets[0] == 10) return true
            // 172.16.0.0/12
            if (octets[0] == 172 && octets[1] in 16..31) return true
            // 192.168.0.0/16
            if (octets[0] == 192 && octets[1] == 168) return true
            // 100.64.0.0/10 (CGNAT/Tailscale)
            if (octets[0] == 100 && octets[1] in 64..127) return true
        }

        return false
    }
}