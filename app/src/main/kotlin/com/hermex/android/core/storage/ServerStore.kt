package com.hermex.android.core.storage

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Persists the list of configured servers and which one is active, across process death. See
 * [CookieStore] for why this is plain DataStore rather than EncryptedSharedPreferences. */
interface ServerStore {
    val state: StateFlow<ServerStoreState>

    /** Re-reads from disk (unlike [state], which may be a cached snapshot). */
    suspend fun load(): ServerStoreState

    /** Adds a new server and returns its config. Becomes the active server only if it's the
     * first one configured -- adding a second server never silently steals focus from whichever
     * one the user is already using. [id] defaults to a fresh UUID; pass one explicitly only if
     * something (cookies/headers) was already scoped to that id before this call, so the
     * persisted server's id matches. */
    suspend fun addServer(name: String, baseUrl: String, id: String = UUID.randomUUID().toString()): HermexServerConfig

    /** Edits an existing server's name/baseUrl in place -- [id] (and therefore its cookies and
     * custom headers, which are keyed by id) never changes. */
    suspend fun updateServer(id: String, name: String, baseUrl: String): HermexServerConfig

    /** Removes a server. If it was the active one and others remain, the first remaining server
     * becomes active; if it was the last server, there is no active server afterward. */
    suspend fun removeServer(id: String)

    /** No-ops if [id] doesn't match any configured server. */
    suspend fun setActiveServer(id: String)

    /** Synchronous, cheap snapshot of the active server -- mirrors [CustomHeadersStore.snapshot]
     * for callers that can't suspend. */
    fun activeServerSnapshot(): HermexServerConfig?
}

/** Default used wherever no real store is wired in -- mirrors [NoOpCustomHeadersStore]. Always
 * empty; every write is a no-op. */
object NoOpServerStore : ServerStore {
    override val state: StateFlow<ServerStoreState> = MutableStateFlow(ServerStoreState())
    override suspend fun load(): ServerStoreState = ServerStoreState()
    override suspend fun addServer(name: String, baseUrl: String, id: String): HermexServerConfig =
        HermexServerConfig(id = id, name = name, baseUrl = baseUrl, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L)
    override suspend fun updateServer(id: String, name: String, baseUrl: String): HermexServerConfig =
        HermexServerConfig(id = id, name = name, baseUrl = baseUrl, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L)
    override suspend fun removeServer(id: String) = Unit
    override suspend fun setActiveServer(id: String) = Unit
    override fun activeServerSnapshot(): HermexServerConfig? = null
}
