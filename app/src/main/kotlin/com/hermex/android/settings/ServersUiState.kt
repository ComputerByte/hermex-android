package com.hermex.android.settings

import com.hermex.android.core.storage.HermexServerConfig

data class ServersUiState(
    val isLoading: Boolean = true,
    val servers: List<HermexServerConfig> = emptyList(),
    val activeServerId: String? = null,
    /** Non-null while switching a server is in flight -- keeps the tapped row's own row from
     * being tappable again mid-switch without blocking the rest of the list. */
    val switchingServerId: String? = null,
    /** Drives the add/edit editor sheet. Null [editingServerId] means "adding a new server";
     * non-null means editing that existing server's name/url in place. */
    val showEditor: Boolean = false,
    val editingServerId: String? = null,
    val editorName: String = "",
    val editorUrl: String = "",
    val editorError: String? = null,
    val isSavingEditor: Boolean = false,
    /** Drives the "remove this server?" confirmation dialog. */
    val serverPendingRemoval: HermexServerConfig? = null,
)
