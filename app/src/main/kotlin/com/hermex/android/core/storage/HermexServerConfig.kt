package com.hermex.android.core.storage

import java.util.UUID
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One configured `hermes-webui` server. [id] is a stable, app-generated identifier (a UUID) --
 * never [baseUrl], since a user can edit the URL of an already-configured server without losing
 * its identity (cookies, custom headers, and "which one is active" are all keyed by [id]). */
@Serializable
data class HermexServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** The full multi-server picture: every configured server, plus which one is active. */
@Serializable
data class ServerStoreState(
    val servers: List<HermexServerConfig> = emptyList(),
    val activeServerId: String? = null,
) {
    /** Not a constructor property -- has no backing field, so kotlinx.serialization's compiler
     * plugin (which only serializes primary-constructor properties) never touches it. */
    val activeServer: HermexServerConfig?
        get() = servers.find { it.id == activeServerId }
}

/** Derives a friendly default display name for a newly added server from its host, e.g.
 * "https://hermex.local/" -> "hermex.local". Falls back to "Default Server" if the URL can't be
 * parsed -- shouldn't happen for an already-normalized URL, but a display label is never worth
 * crashing over. */
fun defaultServerName(baseUrl: String): String = baseUrl.toHttpUrlOrNull()?.host ?: "Default Server"

/**
 * Pure, Context-free transformations over [ServerStoreState] -- extracted from
 * [DataStoreServerStore] so the tricky bits (legacy-URL migration, activeServerId self-healing,
 * add/update/remove/switch semantics) are unit-testable without a real Android
 * Context/DataStore (there's no Robolectric in this project's JVM test source set).
 * [DataStoreServerStore] is a thin wrapper that just reads/writes the JSON blob and delegates
 * every actual decision here.
 */
internal object ServerCollectionOps {
    /** Pre-multi-server installs have exactly one server saved under the legacy unscoped
     * `server_url` key. Migrates it into one [HermexServerConfig] (named from its host) and
     * makes it active, so an existing user is never forced back through onboarding. */
    fun migrateLegacy(legacyUrl: String?): ServerStoreState {
        if (legacyUrl.isNullOrBlank()) return ServerStoreState()
        val now = System.currentTimeMillis()
        val config = HermexServerConfig(
            id = UUID.randomUUID().toString(),
            name = defaultServerName(legacyUrl),
            baseUrl = legacyUrl,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        return ServerStoreState(servers = listOf(config), activeServerId = config.id)
    }

    /** An [ServerStoreState.activeServerId] that doesn't match any configured server (e.g. from
     * decoding a stale/edited collection) self-heals to the first configured server, or to no
     * active server at all if there are none. */
    fun ServerStoreState.selfHealed(): ServerStoreState {
        if (servers.isEmpty()) return if (activeServerId == null) this else copy(activeServerId = null)
        if (servers.any { it.id == activeServerId }) return this
        return copy(activeServerId = servers.first().id)
    }

    /** Only the first server ever configured becomes active automatically -- adding a second (or
     * later) server never silently steals focus from whichever one is already in use. [id]
     * defaults to a fresh UUID, but callers that already scoped cookies/headers to a specific
     * candidate id before persisting (see [com.hermex.android.auth.AuthRepository.login]) can
     * pass it explicitly so the persisted server's id matches what was actually used. */
    fun ServerStoreState.withAddedServer(
        name: String,
        baseUrl: String,
        id: String = UUID.randomUUID().toString(),
    ): Pair<ServerStoreState, HermexServerConfig> {
        val now = System.currentTimeMillis()
        val config = HermexServerConfig(
            id = id,
            name = name,
            baseUrl = baseUrl,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val activeId = activeServerId ?: config.id
        return copy(servers = servers + config, activeServerId = activeId) to config
    }

    /** [id] (and therefore its cookies/custom headers) never changes. Returns the input state
     * unchanged (paired with a synthetic config matching the request) if [id] isn't found --
     * e.g. the server was removed concurrently -- rather than crashing. */
    fun ServerStoreState.withUpdatedServer(id: String, name: String, baseUrl: String): Pair<ServerStoreState, HermexServerConfig> {
        val existing = servers.find { it.id == id } ?: return this to HermexServerConfig(id, name, baseUrl, 0L, 0L)
        val updatedConfig = existing.copy(name = name, baseUrl = baseUrl, updatedAtEpochMillis = System.currentTimeMillis())
        return copy(servers = servers.map { if (it.id == id) updatedConfig else it }) to updatedConfig
    }

    /** If [id] was the active server and others remain, the first remaining server becomes
     * active; if it was the last server, there is no active server afterward. */
    fun ServerStoreState.withRemovedServer(id: String): ServerStoreState {
        val remaining = servers.filter { it.id != id }
        val newActiveId = if (activeServerId == id) remaining.firstOrNull()?.id else activeServerId
        return copy(servers = remaining, activeServerId = newActiveId)
    }

    /** No-op if [id] doesn't match any configured server. */
    fun ServerStoreState.withActiveServer(id: String): ServerStoreState =
        if (servers.none { it.id == id }) this else copy(activeServerId = id)
}
