package com.hermex.android.memory

data class MemoryUiState(
    val isLoading: Boolean = true,
    val memorySection: MemorySectionUi? = null,
    val userSection: MemorySectionUi? = null,
    val soulSection: MemorySectionUi? = null,
    val errorMessage: String? = null,
)

data class MemorySectionUi(
    val content: String?,
    /** Unix seconds -- null if the server didn't report a modification time for this section. */
    val mtime: Double?,
)
