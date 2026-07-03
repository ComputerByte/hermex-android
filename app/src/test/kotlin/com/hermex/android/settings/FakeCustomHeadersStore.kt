package com.hermex.android.settings

import com.hermex.android.core.storage.CustomHeadersStore
import com.hermex.android.core.storage.CustomHttpHeader
import com.hermex.android.core.storage.sanitizedForStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FakeCustomHeadersStore(initial: List<CustomHttpHeader> = emptyList()) : CustomHeadersStore {
    private val _headers = MutableStateFlow(initial)
    override val headers: StateFlow<List<CustomHttpHeader>> = _headers.asStateFlow()
    override fun snapshot(): List<CustomHttpHeader> = _headers.value
    override suspend fun load(): List<CustomHttpHeader> = _headers.value

    // Mirrors DataStoreCustomHeadersStore.save()'s real contract (blank-name rows dropped) --
    // a fake that just stored the draft verbatim wouldn't actually exercise that behavior.
    override suspend fun save(headers: List<CustomHttpHeader>) { _headers.value = headers.sanitizedForStorage() }
}
