package com.hermex.android.core.cache

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's offline cache -- read-only session-list data for now (see [OfflineCacheRepository]);
 * chat/message detail caching is a deferred follow-up. Never holds cookies, custom headers, or
 * any other secret; those have their own DataStore-backed stores.
 *
 * Version 1: no prior on-device schema existed for this data, so there's nothing to migrate from.
 * `exportSchema = false` -- a schema-history directory isn't worth the project clutter for a
 * single-version MVP; revisit once a real migration is needed.
 */
@Database(entities = [CachedSessionEntity::class], version = 1, exportSchema = false)
abstract class HermexDatabase : RoomDatabase() {
    abstract fun cachedSessionDao(): CachedSessionDao
}
