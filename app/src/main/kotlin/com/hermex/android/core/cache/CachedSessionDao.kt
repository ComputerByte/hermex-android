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

    @Query("SELECT * FROM cached_sessions WHERE serverId = :serverId AND sessionId = :sessionId LIMIT 1")
    suspend fun getSession(serverId: String, sessionId: String): CachedSessionEntity?

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

    @Query("SELECT * FROM cached_messages WHERE serverId = :serverId AND sessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getMessages(serverId: String, sessionId: String): List<CachedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessageEntity>)

    @Query("DELETE FROM cached_messages WHERE serverId = :serverId AND sessionId = :sessionId")
    suspend fun deleteMessagesForSession(serverId: String, sessionId: String)

    @Query("DELETE FROM cached_messages WHERE serverId = :serverId")
    suspend fun deleteMessagesForServer(serverId: String)

    /** Replaces one session's cached detail (its own row in `cached_sessions`, which may not
     * exist yet if this session was never seen in the list, plus its full message transcript) in
     * a single transaction -- never a partial merge, since [CachedMessageEntity]'s key isn't a
     * stable server id (see its kdoc). */
    @Transaction
    suspend fun replaceSessionDetail(session: CachedSessionEntity, messages: List<CachedMessageEntity>) {
        insertAll(listOf(session))
        deleteMessagesForSession(session.serverId, session.sessionId)
        insertMessages(messages)
    }
}
