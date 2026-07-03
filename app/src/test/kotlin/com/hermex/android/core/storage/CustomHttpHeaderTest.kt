package com.hermex.android.core.storage

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomHttpHeaderTest {
    @Test
    fun `a valid header name and value is applicable`() {
        assertTrue(CustomHttpHeader(name = "X-Api-Key", value = "secret-token").isApplicable)
        assertTrue(CustomHttpHeader(name = "Authorization", value = "Bearer abc123").isApplicable)
    }

    @Test
    fun `a blank name is not applicable`() {
        assertFalse(CustomHttpHeader(name = "", value = "x").isApplicable)
        assertFalse(CustomHttpHeader(name = "   ", value = "x").isApplicable)
    }

    @Test
    fun `a header name with a space is not applicable`() {
        assertFalse(CustomHttpHeader(name = "Bad Header", value = "x").isApplicable)
    }

    @Test
    fun `a header name with a colon is not applicable`() {
        assertFalse(CustomHttpHeader(name = "Bad:Header", value = "x").isApplicable)
    }

    @Test
    fun `a header name with a newline is not applicable`() {
        assertFalse(CustomHttpHeader(name = "Bad\nHeader", value = "x").isApplicable)
    }

    @Test
    fun `a header value with a newline is not applicable`() {
        assertFalse(CustomHttpHeader(name = "X-Test", value = "line1\nline2").isApplicable)
        assertFalse(CustomHttpHeader(name = "X-Test", value = "line1\rline2").isApplicable)
    }

    @Test
    fun `name and value are trimmed for sanitizedName and sanitizedValue`() {
        val header = CustomHttpHeader(name = "  X-Test-Header  \n", value = "  a value  ")
        assertEquals("X-Test-Header", header.sanitizedName)
        assertEquals("a value", header.sanitizedValue)
    }

    @Test
    fun `all RFC 7230 token special characters are accepted in a header name`() {
        assertTrue(CustomHttpHeader(name = "!#$%&'*+-.^_`|~", value = "x").isApplicable)
    }

    @Test
    fun `a blank-name row is dropped by sanitizedForStorage, others are kept verbatim`() {
        val headers = listOf(
            CustomHttpHeader(name = "", value = "dropped"),
            CustomHttpHeader(name = "   ", value = "also dropped"),
            CustomHttpHeader(name = "X-Keep", value = "kept"),
        )
        val result = headers.sanitizedForStorage()
        assertEquals(1, result.size)
        assertEquals("X-Keep", result.single().name)
    }

    /** Regression test for a real crash: a `private companion object` on this `@Serializable`
     * class hid kotlinx.serialization's synthetic `serializer()`/`Companion` accessor too, not
     * just the constant it was added for -- that compiled fine but threw a runtime
     * `IllegalAccessError` the moment a *different* class (like `DataStoreCustomHeadersStore`,
     * or this test) tried to encode/decode a list of them via `HermexJson`. This test doesn't
     * need DataStore/Context at all -- calling `HermexJson` from this different class is exactly
     * the failure condition, so it would fail the same way if the companion regressed back to
     * `private`. */
    @Test
    fun `HermexJson can encode and decode a list of headers from a different class without an IllegalAccessError`() {
        val headers = listOf(
            CustomHttpHeader(name = "X-Api-Key", value = "secret-token"),
            CustomHttpHeader(name = "Authorization", value = "Bearer abc123"),
        )

        val encoded = HermexJson.encodeToString(headers)
        val decoded = HermexJson.decodeFromString<List<CustomHttpHeader>>(encoded)

        assertEquals(headers, decoded)
    }
}
