package com.hermex.android.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull

private val Context.appearancePreferencesDataStore by preferencesDataStore(name = "hermex_appearance_preferences")

class DataStoreAppearancePreferencesStore(private val context: Context) : AppearancePreferencesStore {
    private val headerLogoColorKey = stringPreferencesKey("header_logo_color")

    override suspend fun loadHeaderLogoColor(): HeaderLogoColor {
        val stored = context.appearancePreferencesDataStore.data.firstOrNull()?.get(headerLogoColorKey)
        return HeaderLogoColor.fromStoredNameOrDefault(stored)
    }

    override suspend fun setHeaderLogoColor(color: HeaderLogoColor) {
        context.appearancePreferencesDataStore.edit { prefs -> prefs[headerLogoColorKey] = color.name }
    }
}
