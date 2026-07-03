package com.hermex.android.core.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CachedSessionDao {
    @Query("SELECT * FROM cached_sessions WHERE serverId = :serverId ORDER BY lastMessageAt DESC")
    suspend fun getSessions(serverId: String): List<CachedSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<CachedSessionEntity>)

    @Query("DELETE FROM cached_sessions WHERE serverId = :serverId")
    suspend fun deleteSessionsForServer(serverId: String)

    /** Replaces the entire cached session list for [serverId] -- a session that's been deleted
     * or archived away server-side should disappear from the cache too, not linger forever, so
     * every successful list fetch fully replaces (rather than merges into) what's stored. */
    @Transaction
    suspend fun replaceSessions(serverId: String, sessions: List<CachedSessionEntity>) {
        deleteSessionsForServer(serverId)
        insertAll(sessions)
    }
}
