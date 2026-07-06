package com.hermex.android.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull

private val Context.chatPreferencesDataStore by preferencesDataStore(name = "hermex_chat_preferences")

class DataStoreChatPreferencesStore(private val context: Context) : ChatPreferencesStore {
    private val expandThinkingKey = booleanPreferencesKey("expand_thinking_by_default")
    private val expandToolCallsKey = booleanPreferencesKey("expand_tool_calls_by_default")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val showSubagentSessionsKey = booleanPreferencesKey("show_subagent_sessions")

    override suspend fun loadExpandThinkingByDefault(): Boolean =
        context.chatPreferencesDataStore.data.firstOrNull()?.get(expandThinkingKey) ?: false

    override suspend fun setExpandThinkingByDefault(value: Boolean) {
        context.chatPreferencesDataStore.edit { prefs -> prefs[expandThinkingKey] = value }
    }

    override suspend fun loadExpandToolCallsByDefault(): Boolean =
        context.chatPreferencesDataStore.data.firstOrNull()?.get(expandToolCallsKey) ?: false

    override suspend fun setExpandToolCallsByDefault(value: Boolean) {
        context.chatPreferencesDataStore.edit { prefs -> prefs[expandToolCallsKey] = value }
    }

    override suspend fun loadNotificationsEnabled(): Boolean =
        context.chatPreferencesDataStore.data.firstOrNull()?.get(notificationsEnabledKey) ?: false

    override suspend fun setNotificationsEnabled(value: Boolean) {
        context.chatPreferencesDataStore.edit { prefs -> prefs[notificationsEnabledKey] = value }
    }

    override suspend fun loadShowSubagentSessions(): Boolean =
        context.chatPreferencesDataStore.data.firstOrNull()?.get(showSubagentSessionsKey) ?: true

    override suspend fun setShowSubagentSessions(value: Boolean) {
        context.chatPreferencesDataStore.edit { prefs -> prefs[showSubagentSessionsKey] = value }
    }
}
