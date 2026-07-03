package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@Serializable
private data class ContentHolder(
    @Serializable(with = MessageContentSerializer::class) val content: String? = null,
)

/**
 * `content` is the one field on the wire whose *shape*, not just its primitive type, varies:
 * plain string vs. a structured array of parts. These tests cover every array shape the parser
 * needs to survive without throwing, per the MVP contract in API_CONTRACT.md ("extract plain
 * string content only, treat non-string content as null rather than crashing").
 */
class MessageContentSerializerTest {
    @Test
    fun `plain string content decodes as-is`() {
        val result = HermexJson.decodeFromString<ContentHolder>("""{"content":"hello"}""")
        assertEquals("hello", result.content)
    }

    @Test
    fun `null content decodes as null`() {
        val result = HermexJson.decodeFromString<ContentHolder>("""{"content":null}""")
        assertNull(result.content)
    }

    @Test
    fun `missing content field decodes as null`() {
        val result = HermexJson.decodeFromString<ContentHolder>("""{}""")
        assertNull(result.content)
    }

    @Test
    fun `structured array with text parts concatenates them`() {
        val result = HermexJson.decodeFromString<ContentHolder>(
            """{"content":[{"type":"text","text":"hello "},{"type":"text","text":"world"}]}""",
        )
        assertEquals("hello world", result.content)
    }

    @Test
    fun `structured array with a block that has no text field does not crash and yields null`() {
        val result = HermexJson.decodeFromString<ContentHolder>(
            """{"content":[{"type":"image","source":{"url":"https://example.com/a.png"}}]}""",
        )
        assertNull(result.content)
    }

    @Test
    fun `structured array mixing text and non-text blocks extracts only the text`() {
        val result = HermexJson.decodeFromString<ContentHolder>(
            """{"content":[{"type":"image","source":{}},{"type":"text","text":"caption"}]}""",
        )
        assertEquals("caption", result.content)
    }

    @Test
    fun `empty array does not crash and yields null`() {
        val result = HermexJson.decodeFromString<ContentHolder>("""{"content":[]}""")
        assertNull(result.content)
    }

    @Test
    fun `array of bare primitives (no objects at all) does not crash and yields null`() {
        val result = HermexJson.decodeFromString<ContentHolder>("""{"content":[1,"two",true,null]}""")
        assertNull(result.content)
    }

    @Test
    fun `text field present but not a string (nested object) does not crash and yields null for that block`() {
        val result = HermexJson.decodeFromString<ContentHolder>(
            """{"content":[{"type":"text","text":{"unexpected":"shape"}}]}""",
        )
        assertNull(result.content)
    }

    @Test
    fun `bare JSON object (not array, not string, not null) does not crash and yields null`() {
        val result = HermexJson.decodeFromString<ContentHolder>("""{"content":{"unexpected":"shape"}}""")
        assertNull(result.content)
    }

    @Test
    fun `numeric content coerces to its string form (matches lenient primitive behavior elsewhere)`() {
        val result = HermexJson.decodeFromString<ContentHolder>("""{"content":42}""")
        assertEquals("42", result.content)
    }

    @Test
    fun `encoding a non-null value round-trips as a plain JSON string`() {
        val encoded = HermexJson.encodeToString(ContentHolder("hi"))
        assertEquals("""{"content":"hi"}""", encoded)
    }
}
