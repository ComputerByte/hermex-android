package com.hermex.android.core.cache

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds `cached_messages` (chat/message offline cache, see [CachedMessageEntity]) without touching
 * the existing `cached_sessions` table -- an existing install's already-cached session list
 * survives this upgrade untouched. The exact SQL below matches what Room itself generates for
 * [CachedMessageEntity] (verified against the generated `HermexDatabase_Impl` before writing this).
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_messages` (`serverId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                "`orderIndex` INTEGER NOT NULL, `role` TEXT, `content` TEXT, `reasoning` TEXT, `timestamp` REAL, " +
                "`messageId` TEXT, `name` TEXT, `toolCallId` TEXT, `cachedAtEpochMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`serverId`, `sessionId`, `orderIndex`))",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_messages_serverId_sessionId` ON `cached_messages` (`serverId`, `sessionId`)",
        )
    }
}
