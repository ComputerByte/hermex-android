package com.hermex.android.core.cache

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's offline cache -- read-only session-list and chat/message data (see
 * [OfflineCacheRepository]). Never holds cookies, custom headers, or any other secret; those have
 * their own DataStore-backed stores.
 *
 * Version 2 added `cached_messages` (see [MIGRATION_1_2]) without touching the existing
 * `cached_sessions` table, so upgrading doesn't lose already-cached session lists.
 * `exportSchema = false` -- a schema-history directory isn't worth the project clutter for this
 * app's current version count; revisit if migrations get more involved.
 */
@Database(entities = [CachedSessionEntity::class, CachedMessageEntity::class], version = 2, exportSchema = false)
abstract class HermexDatabase : RoomDatabase() {
    abstract fun cachedSessionDao(): CachedSessionDao
}
