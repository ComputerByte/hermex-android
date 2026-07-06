package com.hermex.android.core.storage

/** Local appearance preferences -- distinct from [ServerStore]/[CookieStore], which persist
 * auth/connection state. See [CookieStore] for why this is plain DataStore rather than
 * EncryptedSharedPreferences (nothing here is sensitive either). Mirrors [ChatPreferencesStore]. */
interface AppearancePreferencesStore {
    suspend fun loadHeaderLogoColor(): HeaderLogoColor
    suspend fun setHeaderLogoColor(color: HeaderLogoColor)
    suspend fun loadAppIconVariant(): AppIconVariant
    suspend fun setAppIconVariant(variant: AppIconVariant)
    suspend fun loadUserInitials(): String
    suspend fun setUserInitials(initials: String)
}

/** Default used wherever no real store is wired in -- mirrors [NoOpCustomHeadersStore]. Always
 * the default value for each preference; every write is a no-op. */
object NoOpAppearancePreferencesStore : AppearancePreferencesStore {
    override suspend fun loadHeaderLogoColor(): HeaderLogoColor = HeaderLogoColor.DEFAULT
    override suspend fun setHeaderLogoColor(color: HeaderLogoColor) = Unit
    override suspend fun loadAppIconVariant(): AppIconVariant = AppIconVariant.SYSTEM
    override suspend fun setAppIconVariant(variant: AppIconVariant) = Unit
    override suspend fun loadUserInitials(): String = "BD"
    override suspend fun setUserInitials(initials: String) = Unit
}
