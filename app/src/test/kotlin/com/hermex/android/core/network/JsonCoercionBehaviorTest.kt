package com.hermex.android.core.network

import com.hermex.android.core.util.LossyBooleanSerializer
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Test

@Serializable
private data class StringField(val v: String? = null)

@Serializable
private data class IntField(val v: Int? = null)

@Serializable
private data class BoolField(val v: Boolean? = null)

@Serializable
private data class LossyBoolField(
    @Serializable(with = LossyBooleanSerializer::class) val v: Boolean? = null,
)

/**
 * Documents exactly what [HermexJson]'s `isLenient` + `coerceInputValues` config coerces for
 * free, and what it doesn't -- the empirical basis for only adding a custom serializer where
 * one is actually needed (see [com.hermex.android.core.util.LossyBooleanSerializer]) instead of
 * wrapping every field defensively.
 */
class JsonCoercionBehaviorTest {
    @Test
    fun `numeric JSON value coerces into a plain String field`() {
        val result = HermexJson.decodeFromString<StringField>("""{"v":42}""")
        assertEquals("42", result.v)
    }

    @Test
    fun `quoted numeric string coerces into a plain Int field`() {
        val result = HermexJson.decodeFromString<IntField>("""{"v":"42"}""")
        assertEquals(42, result.v)
    }

    @Test
    fun `quoted boolean string coerces into a plain Boolean field`() {
        val result = HermexJson.decodeFromString<BoolField>("""{"v":"true"}""")
        assertEquals(true, result.v)
    }

    @Test(expected = Exception::class)
    fun `numeric 1 into a plain Boolean field is NOT coerced -- this is why LossyBooleanSerializer exists`() {
        HermexJson.decodeFromString<BoolField>("""{"v":1}""")
    }

    @Test
    fun `numeric 1 and 0 into a LossyBooleanSerializer-annotated field IS coerced`() {
        assertEquals(true, HermexJson.decodeFromString<LossyBoolField>("""{"v":1}""").v)
        assertEquals(false, HermexJson.decodeFromString<LossyBoolField>("""{"v":0}""").v)
    }

    @Test
    fun `yes and no strings into a LossyBooleanSerializer-annotated field ARE coerced`() {
        assertEquals(true, HermexJson.decodeFromString<LossyBoolField>("""{"v":"yes"}""").v)
        assertEquals(false, HermexJson.decodeFromString<LossyBoolField>("""{"v":"no"}""").v)
    }

    @Test
    fun `unknown extra fields are ignored, not fatal`() {
        val result = HermexJson.decodeFromString<StringField>("""{"v":"hi","totally_unexpected":{"nested":true}}""")
        assertEquals("hi", result.v)
    }
}
