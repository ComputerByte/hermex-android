package com.hermex.android.core.appicon

import com.hermex.android.core.storage.AppIconVariant

/**
 * Orchestrates switching the app's launcher icon: resolves [AppIconVariant] to a concrete alias
 * (see [AppIconResolver]) and enables exactly that one via [aliasWriter], disabling the other two.
 * [isDarkModeProvider] is a plain function rather than a live/observed value -- see
 * [AppIconResolver] for why "follow the system" only ever means "resolve right now."
 */
class AppIconSwitcher(
    private val aliasWriter: AppIconAliasWriter,
    private val isDarkModeProvider: () -> Boolean,
) {
    /** Enables the alias [variant] resolves to under the current device appearance, and disables
     * every other alias. Safe to call unconditionally (e.g. on every app start) -- re-enabling an
     * already-enabled alias is a harmless no-op. */
    fun applyVariant(variant: AppIconVariant) {
        val target = resolvedAlias(variant)
        ConcreteAppIcon.entries.forEach { alias -> aliasWriter.setEnabled(alias, alias == target) }
    }

    /** The alias [variant] currently resolves to, given the device's appearance right now. */
    fun resolvedAlias(variant: AppIconVariant): ConcreteAppIcon = AppIconResolver.resolve(variant, isDarkModeProvider())
}
