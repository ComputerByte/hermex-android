package com.hermex.android.settings

import com.hermex.android.core.storage.HeaderLogoColor

data class SettingsUiState(
    val isLoading: Boolean = true,
    val serverUrl: String? = null,
    /** The active server's user-editable display name (see [com.hermex.android.core.storage.HermexServerConfig]). */
    val activeServerName: String? = null,
    val serverVersion: String? = null,
    /** The server's remembered default model id, shown as-is (matching iOS, which also shows the
     * raw model id here rather than a decorated display name). */
    val defaultModel: String? = null,
    val isSigningOut: Boolean = false,
    val errorMessage: String? = null,
    val expandThinkingByDefault: Boolean = false,
    /** Count of saved custom headers (after blank-name rows are dropped) -- drives the
     * "None" / "N configured" subtitle on the Connection Headers row. */
    val customHeaderCount: Int = 0,
    val headerLogoColor: HeaderLogoColor = HeaderLogoColor.DEFAULT,
)
