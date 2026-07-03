package com.hermex.android.tasks

import com.hermex.android.core.storage.ServerStore

internal class FakeServerStore(initial: String?) : ServerStore {
    var stored: String? = initial
    override suspend fun save(serverUrl: String) { stored = serverUrl }
    override suspend fun load(): String? = stored
    override suspend fun clear() { stored = null }
}
