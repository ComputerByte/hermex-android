package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `explicitNulls = false` is what makes an all-null request body encode as `{}` rather than
 * `{"workspace":null,"model":null,...}`. This matters because the iOS client omits absent
 * fields entirely, and some servers treat an explicit `null` differently from a missing key
 * (e.g. "clear this field" vs. "don't touch this field"). Verified here rather than assumed.
 */
class SessionDtoSerializationTest {
    @Test
    fun `NewSessionRequest with all-null optionals encodes as an empty object`() {
        val encoded = HermexJson.encodeToString(NewSessionRequest())
        assertEquals("{}", encoded)
    }

    @Test
    fun `NewSessionRequest with only one field set omits every other null field`() {
        val encoded = HermexJson.encodeToString(NewSessionRequest(workspace = "/home/user/project"))
        assertEquals("""{"workspace":"/home/user/project"}""", encoded)
    }

    @Test
    fun `NewSessionRequest with all fields set encodes every one, snake_case`() {
        val encoded = HermexJson.encodeToString(
            NewSessionRequest(
                workspace = "/home/user/project",
                model = "claude-sonnet-5",
                modelProvider = "anthropic",
                profile = "default",
            ),
        )
        assertEquals(
            """{"workspace":"/home/user/project","model":"claude-sonnet-5","model_provider":"anthropic","profile":"default"}""",
            encoded,
        )
    }

    @Test
    fun `LoginRequest's required non-nullable field is always emitted even though it could theoretically be blank`() {
        val encoded = HermexJson.encodeToString(LoginRequest(password = ""))
        assertEquals("""{"password":""}""", encoded)
    }

    @Test
    fun `EmptyRequestBody encodes as an empty object for logout`() {
        val encoded = HermexJson.encodeToString(EmptyRequestBody)
        assertEquals("{}", encoded)
    }
}
