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
}
