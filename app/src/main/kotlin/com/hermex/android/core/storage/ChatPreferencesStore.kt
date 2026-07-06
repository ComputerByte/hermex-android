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
