package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileDtoTest {
    @Test
    fun `effectiveDefaultProfileName prefers the echoed 'active' name`() {
        val response = HermexJson.decodeFromString<ProfilesResponse>(
            """{"active":"work","profiles":[{"name":"default","is_default":true}]}""",
        )
        assertEquals("work", response.effectiveDefaultProfileName)
    }

    @Test
    fun `effectiveDefaultProfileName falls back to is_active, then is_default, then the first profile`() {
        val byIsActive = HermexJson.decodeFromString<ProfilesResponse>(
            """{"profiles":[{"name":"a"},{"name":"b","is_active":true}]}""",
        )
        assertEquals("b", byIsActive.effectiveDefaultProfileName)

        val byIsDefault = HermexJson.decodeFromString<ProfilesResponse>(
            """{"profiles":[{"name":"a"},{"name":"b","is_default":true}]}""",
        )
        assertEquals("b", byIsDefault.effectiveDefaultProfileName)

        val byFirst = HermexJson.decodeFromString<ProfilesResponse>(
            """{"profiles":[{"name":"a"},{"name":"b"}]}""",
        )
        assertEquals("a", byFirst.effectiveDefaultProfileName)
    }

    @Test
    fun `effectiveDefaultProfileName is null when nothing resolves`() {
        assertNull(HermexJson.decodeFromString<ProfilesResponse>("""{}""").effectiveDefaultProfileName)
        assertNull(HermexJson.decodeFromString<ProfilesResponse>("""{"profiles":[]}""").effectiveDefaultProfileName)
    }

    @Test
    fun `blank 'active' string is treated as absent`() {
        val response = HermexJson.decodeFromString<ProfilesResponse>(
            """{"active":"   ","profiles":[{"name":"a","is_default":true}]}""",
        )
        assertEquals("a", response.effectiveDefaultProfileName)
    }

    @Test
    fun `displayName renders 'default' as 'Default' and falls back to 'Profile' when unnamed`() {
        assertEquals("Default", ProfileSummary(name = "default").displayName)
        assertEquals("work", ProfileSummary(name = "work").displayName)
        assertEquals("Profile", ProfileSummary(name = null).displayName)
        assertEquals("Profile", ProfileSummary(name = "   ").displayName)
    }

    @Test
    fun `subtitle joins model, provider, and skill count -- and is null when all are absent`() {
        assertEquals(
            "gpt-4 · OpenAI - 5 skills",
            ProfileSummary(model = "gpt-4", provider = "OpenAI", skillCount = 5).subtitle,
        )
        assertEquals("1 skill", ProfileSummary(skillCount = 1).subtitle)
        assertEquals("gpt-4", ProfileSummary(model = "gpt-4").subtitle)
        assertNull(ProfileSummary().subtitle)
    }

    @Test
    fun `full profiles list response decodes tolerantly`() {
        val response = HermexJson.decodeFromString<ProfilesResponse>(
            """{"active":"work","profiles":[
                {"name":"default","is_default":true,"model":"gpt-4","provider":"OpenAI","skill_count":3},
                {"name":"work","is_active":true,"gateway_running":true,"has_env":true}
            ]}""",
        )
        assertEquals(2, response.profiles?.size)
        assertEquals("work", response.active)
    }

    @Test
    fun `profile switch response decodes the extra default_model and default_workspace fields`() {
        val response = HermexJson.decodeFromString<ProfileSwitchResponse>(
            """{"active":"work","default_model":"gpt-4","default_workspace":"/home/work"}""",
        )
        assertEquals("work", response.active)
        assertEquals("gpt-4", response.defaultModel)
        assertEquals("/home/work", response.defaultWorkspace)
        assertNull(response.error)
    }

    @Test
    fun `profile switch response surfaces a server-side error`() {
        val response = HermexJson.decodeFromString<ProfileSwitchResponse>(
            """{"error":"profile not found"}""",
        )
        assertEquals("profile not found", response.error)
    }
}
