package com.hermex.android.models

import com.hermex.android.core.network.dto.ModelCatalogGroup

data class DefaultModelUiState(
    val isLoading: Boolean = true,
    val groups: List<ModelCatalogGroup> = emptyList(),
    val defaultModelId: String? = null,
    /** Id of the model currently being saved -- distinct from [isLoading] so picking a row shows
     * a small inline spinner rather than blanking the whole list. */
    val savingModelId: String? = null,
    val errorMessage: String? = null,
)
