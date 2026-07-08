package com.hermex.android.core.storage

import kotlinx.serialization.Serializable

/** A single user-supplied HTTP request header sent with every outgoing request -- for
 * self-hosters behind an authenticated reverse proxy (e.g. Authentik) or a token-gated server
 * that the password-only login flow can't reach on its own. Mirrors iOS's `CustomHeader`
 * (`Networking/CustomHeader.swift`). */
@Serializable
data class CustomHttpHeader(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val value: String = "",
) {
    /** Leading/trailing whitespace and newlines trimmed; internal spaces (e.g. "Bearer <token>")
     * are preserved. */
    val sanitizedName: String get() = name.trim()
    val sanitizedValue: String get() = value.trim()

    /** A header is usable when its name is a non-empty RFC 7230 `token` (so no spaces or colons,
     * which would either be silently dropped or make the header ambiguous) and its value has no
     * newline (which would allow HTTP header injection). Half-typed/blank rows are simply
     * skipped, never sent -- matching iOS's `isApplicable` exactly. */
    val isApplicable: Boolean
        get() {
            val trimmedName = sanitizedName
            if (trimmedName.isEmpty()) return false
            if (!trimmedName.all { it in HEADER_NAME_ALLOWED_CHARS }) return false
            return value.none { it == '\n' || it == '\r' }
        }
}

/** RFC 7230 `token` characters allowed in a header field name. A plain top-level (not companion
 * object) constant: a `private companion object` on a `@Serializable` class hides the
 * kotlinx.serialization plugin's synthetic `serializer()`/`Companion` accessor too, not just this
 * constant -- that caused a runtime `IllegalAccessError` from [DataStoreCustomHeadersStore] (a
 * different file/class) the first time it tried to encode a [CustomHttpHeader] list, despite
 * compiling cleanly. */
private val HEADER_NAME_ALLOWED_CHARS: Set<Char> =
    ('0'..'9').toSet() + ('a'..'z').toSet() + ('A'..'Z').toSet() + "!#$%&'*+-.^_`|~".toSet()

/** Drops rows with no usable name (blank/whitespace-only) so half-typed placeholder rows aren't
 * persisted; the rest are kept verbatim -- a row that's saved but still fails
 * [CustomHttpHeader.isApplicable] for another reason (e.g. a space in the name) is simply skipped
 * again at request time, matching iOS's `sanitizedForStorage()`. */
fun List<CustomHttpHeader>.sanitizedForStorage(): List<CustomHttpHeader> =
    filter { it.sanitizedName.isNotEmpty() }
