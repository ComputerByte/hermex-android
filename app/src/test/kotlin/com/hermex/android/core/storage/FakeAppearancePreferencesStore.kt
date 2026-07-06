package com.hermex.android.core.storage

/** Shared in-memory [AppearancePreferencesStore] test double -- avoids needing a real Android
 * Context/DataStore. */
internal class FakeAppearancePreferencesStore(
    initialHeaderLogoColor: HeaderLogoColor = HeaderLogoColor.DEFAULT,
    initialAppIconVariant: AppIconVariant = AppIconVariant.SYSTEM,
    initialUserInitials: String = "BD",
) : AppearancePreferencesStore {
    var stored: HeaderLogoColor = initialHeaderLogoColor
    var storedIcon: AppIconVariant = initialAppIconVariant
    var storedInitials: String = initialUserInitials

    override suspend fun loadHeaderLogoColor(): HeaderLogoColor = stored

    override suspend fun setHeaderLogoColor(color: HeaderLogoColor) {
        stored = color
    }

    override suspend fun loadAppIconVariant(): AppIconVariant = storedIcon

    override suspend fun setAppIconVariant(variant: AppIconVariant) {
        storedIcon = variant
    }

    override suspend fun loadUserInitials(): String = storedInitials

    override suspend fun setUserInitials(initials: String) {
        storedInitials = initials
    }
}
