package com.hermex.android.core.storage

/** Local chat display preferences -- distinct from [ServerStore]/[CookieStore], which persist
 * auth/connection state. See [CookieStore] for why this is plain DataStore rather than
 * EncryptedSharedPreferences (nothing here is sensitive either). */
interface ChatPreferencesStore {
    suspend fun loadExpandThinkingByDefault(): Boolean
    suspend fun setExpandThinkingByDefault(value: Boolean)
    suspend fun loadExpandToolCallsByDefault(): Boolean
    suspend fun setExpandToolCallsByDefault(value: Boolean)
    suspend fun loadNotificationsEnabled(): Boolean
    suspend fun setNotificationsEnabled(value: Boolean)
    suspend fun loadShowSubagentSessions(): Boolean
    suspend fun setShowSubagentSessions(value: Boolean)
}

/** Default used wherever no real store is wired in -- mirrors [NoOpAppearancePreferencesStore].
 * Always the default value for each preference; every write is a no-op. */
object NoOpChatPreferencesStore : ChatPreferencesStore {
    override suspend fun loadExpandThinkingByDefault(): Boolean = false
    override suspend fun setExpandThinkingByDefault(value: Boolean) = Unit
    override suspend fun loadExpandToolCallsByDefault(): Boolean = false
    override suspend fun setExpandToolCallsByDefault(value: Boolean) = Unit
    override suspend fun loadNotificationsEnabled(): Boolean = false
    override suspend fun setNotificationsEnabled(value: Boolean) = Unit
    override suspend fun loadShowSubagentSessions(): Boolean = true
    override suspend fun setShowSubagentSessions(value: Boolean) = Unit
}
