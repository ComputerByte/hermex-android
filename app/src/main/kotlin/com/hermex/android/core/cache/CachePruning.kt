package com.hermex.android.core.cache

import java.util.concurrent.TimeUnit

/** Conservative default retention policy for [OfflineCacheRepository.pruneServerCache] -- see
 * [sessionIdsToPrune]'s kdoc for exactly how these combine. */
const val DEFAULT_MAX_CACHED_SESSIONS_PER_SERVER = 50
const val DEFAULT_MAX_CACHE_AGE_DAYS = 90

/**
 * Decides which of [sessions]' ids should be pruned under a conservative retention policy:
 * [sessionIdToPreserve] (if present) is always kept; of the rest, the [maxSessions] most recently
 * active (by [CachedSessionEntity.lastMessageAt], falling back to [CachedSessionEntity.cachedAtEpochMillis]
 * -- normalized to the same epoch-seconds scale -- when null) are kept; and finally anything among
 * those that's still older than [maxAgeDays] (measured from [nowEpochMillis] against
 * [CachedSessionEntity.cachedAtEpochMillis]) is dropped too. Both the count cap and the age cap
 * must be satisfied to survive -- this is a hardening pass, not a "whichever is more generous"
 * policy.
 *
 * Pure and stateless so this policy is plain-JVM-unit-testable without a real Room database (this
 * project's test source set has no Robolectric/instrumented setup -- see
 * [FakeOfflineCacheRepository]'s kdoc for the same reasoning applied to the repository interface
 * itself). [RoomOfflineCacheRepository.pruneServerCache] loads entities via the DAO and calls this
 * to decide what to delete; it never re-implements the policy itself.
 */
fun sessionIdsToPrune(
    sessions: List<CachedSessionEntity>,
    maxSessions: Int,
    maxAgeDays: Int,
    nowEpochMillis: Long,
    sessionIdToPreserve: String?,
): Set<String> {
    val candidates = sessions.filter { it.sessionId != sessionIdToPreserve }
    val mostRecentFirst = candidates.sortedByDescending { it.lastMessageAt ?: (it.cachedAtEpochMillis / 1000.0) }
    val cutoffEpochMillis = nowEpochMillis - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
    val kept = mostRecentFirst.take(maxSessions).filter { it.cachedAtEpochMillis >= cutoffEpochMillis }
    val keptIds = kept.map { it.sessionId }.toSet() + setOfNotNull(sessionIdToPreserve)
    return sessions.map { it.sessionId }.toSet() - keptIds
}
