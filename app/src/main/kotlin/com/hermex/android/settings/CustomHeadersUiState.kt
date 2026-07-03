package com.hermex.android.settings

import com.hermex.android.core.storage.CustomHttpHeader

data class CustomHeadersUiState(
    val isLoading: Boolean = true,
    /** Editable draft rows -- only written to the store when [CustomHeadersViewModel.save] is
     * called, so navigating back without saving discards any in-progress edit. */
    val headers: List<CustomHttpHeader> = emptyList(),
    val isSaving: Boolean = false,
)
