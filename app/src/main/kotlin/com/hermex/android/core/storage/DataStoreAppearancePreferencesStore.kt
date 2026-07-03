package com.hermex.android.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull

private val Context.appearancePreferencesDataStore by preferencesDataStore(name = "hermex_appearance_preferences")

class DataStoreAppearancePreferencesStore(private val context: Context) : AppearancePreferencesStore {
    private val headerLogoColorKey = stringPreferencesKey("header_logo_color")
    private val appIconVariantKey = stringPreferencesKey("app_icon_variant")

    override suspend fun loadHeaderLogoColor(): HeaderLogoColor {
        val stored = context.appearancePreferencesDataStore.data.firstOrNull()?.get(headerLogoColorKey)
        return HeaderLogoColor.fromStoredNameOrDefault(stored)
    }

    override suspend fun setHeaderLogoColor(color: HeaderLogoColor) {
        context.appearancePreferencesDataStore.edit { prefs -> prefs[headerLogoColorKey] = color.name }
    }

    override suspend fun loadAppIconVariant(): AppIconVariant {
        val stored = context.appearancePreferencesDataStore.data.firstOrNull()?.get(appIconVariantKey)
        return AppIconVariant.fromStoredNameOrDefault(stored)
    }

    override suspend fun setAppIconVariant(variant: AppIconVariant) {
        context.appearancePreferencesDataStore.edit { prefs -> prefs[appIconVariantKey] = variant.name }
    }
}
