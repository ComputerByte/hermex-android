package com.hermex.android

import com.hermex.android.core.cache.DEFAULT_MAX_CACHE_AGE_DAYS
import com.hermex.android.core.cache.DEFAULT_MAX_CACHED_SESSIONS_PER_SERVER
import com.hermex.android.core.cache.OfflineCacheRepository
import com.hermex.android.core.storage.HermexServerConfig

/**
 * Prunes every server in [servers] through [cache] under the app's conservative default
 * retention policy, with no active/current session known to preserve -- see
 * [AppContainer.pruneOfflineCaches]'s kdoc for why nothing is open yet this early in the app's
 * lifecycle. Extracted as a standalone function (rather than inlined into [AppContainer]) purely
 * so this startup-hook loop is plain-JVM-unit-testable against a fake [OfflineCacheRepository]/
 * server list pair, without needing a real Android [android.content.Context] the way
 * constructing an [AppContainer] would.
 */
suspend fun pruneAllServerCaches(servers: List<HermexServerConfig>, cache: OfflineCacheRepository) {
    servers.forEach { server ->
        cache.pruneServerCache(
            serverId = server.id,
            maxSessions = DEFAULT_MAX_CACHED_SESSIONS_PER_SERVER,
            maxAgeDays = DEFAULT_MAX_CACHE_AGE_DAYS,
            sessionIdToPreserve = null,
        )
    }
}
