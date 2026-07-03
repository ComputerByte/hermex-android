package com.hermex.android.core.storage

/** Shared in-memory [AppearancePreferencesStore] test double -- avoids needing a real Android
 * Context/DataStore. */
internal class FakeAppearancePreferencesStore(initial: HeaderLogoColor = HeaderLogoColor.DEFAULT) : AppearancePreferencesStore {
    var stored: HeaderLogoColor = initial

    override suspend fun loadHeaderLogoColor(): HeaderLogoColor = stored

    override suspend fun setHeaderLogoColor(color: HeaderLogoColor) {
        stored = color
    }
}
