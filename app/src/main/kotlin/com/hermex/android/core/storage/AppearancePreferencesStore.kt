package com.hermex.android.core.storage

/** Local appearance preferences -- distinct from [ServerStore]/[CookieStore], which persist
 * auth/connection state. See [CookieStore] for why this is plain DataStore rather than
 * EncryptedSharedPreferences (nothing here is sensitive either). Mirrors [ChatPreferencesStore]. */
interface AppearancePreferencesStore {
    suspend fun loadHeaderLogoColor(): HeaderLogoColor
    suspend fun setHeaderLogoColor(color: HeaderLogoColor)
}

/** Default used wherever no real store is wired in -- mirrors [NoOpCustomHeadersStore]. Always
 * [HeaderLogoColor.DEFAULT]; every write is a no-op. */
object NoOpAppearancePreferencesStore : AppearancePreferencesStore {
    override suspend fun loadHeaderLogoColor(): HeaderLogoColor = HeaderLogoColor.DEFAULT
    override suspend fun setHeaderLogoColor(color: HeaderLogoColor) = Unit
}
