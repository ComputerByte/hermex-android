package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerSettingsDtoTest {
    @Test
    fun `displayVersion prefers 'version' over 'webui_version'`() {
        val response = HermexJson.decodeFromString<ServerSettingsResponse>(
            """{"version":"v0.51.766","webui_version":"0.51.0"}""",
        )
        assertEquals("v0.51.766", response.displayVersion)
    }

    @Test
    fun `displayVersion falls back to 'webui_version' when 'version' is absent`() {
        val response = HermexJson.decodeFromString<ServerSettingsResponse>("""{"webui_version":"0.51.0"}""")
        assertEquals("0.51.0", response.displayVersion)
    }

    @Test
    fun `displayVersion is null when neither field is present`() {
        assertNull(HermexJson.decodeFromString<ServerSettingsResponse>("""{}""").displayVersion)
    }

    @Test
    fun `decodes tolerantly with extra unknown fields`() {
        val response = HermexJson.decodeFromString<ServerSettingsResponse>(
            """{"bot_name":"Hermes","version":"v1.0","theme":"dark","unknown_field":123}""",
        )
        assertEquals("Hermes", response.botName)
        assertEquals("dark", response.theme)
    }
}
