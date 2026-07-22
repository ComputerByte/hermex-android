package com.hermex.android.core.storage

import com.hermex.android.core.font.FontFamilyOption
import kotlinx.coroutines.flow.Flow

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

    // ── Font preferences ──────────────────────────────────────────────────────────
    /** The stored UI font key (see [FontFamilyOption.storageKey]). Defaults to [FontFamilyOption.SystemDefault.storageKey]. */
    suspend fun loadUiFontFamily(): String
    suspend fun setUiFontFamily(fontKey: String)
    /** Reactive stream of the UI font key — used by [MainActivity] so the theme can react immediately. */
    fun observeUiFontFamily(): Flow<String>

    /** The stored monospace font key. Defaults to [FontFamilyOption.SystemDefault.storageKey]. */
    suspend fun loadMonospaceFontFamily(): String
    suspend fun setMonospaceFontFamily(fontKey: String)
    /** Reactive stream of the monospace font key. */
    fun observeMonospaceFontFamily(): Flow<String>
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

    override suspend fun loadUiFontFamily(): String = FontFamilyOption.SystemDefault.storageKey
    override suspend fun setUiFontFamily(fontKey: String) = Unit
    override fun observeUiFontFamily(): Flow<String> = kotlinx.coroutines.flow.flowOf(FontFamilyOption.SystemDefault.storageKey)
    override suspend fun loadMonospaceFontFamily(): String = FontFamilyOption.SystemDefault.storageKey
    override suspend fun setMonospaceFontFamily(fontKey: String) = Unit
    override fun observeMonospaceFontFamily(): Flow<String> = kotlinx.coroutines.flow.flowOf(FontFamilyOption.SystemDefault.storageKey)
}
