package com.hermex.android.core.appicon

import com.hermex.android.core.storage.AppIconVariant

/** Pure, Context-free mapping from a user's [AppIconVariant] choice to the concrete launcher
 * alias that should be enabled -- extracted so it's unit-testable without a real Android Context
 * (there's no Robolectric in this project's JVM test source set). */
object AppIconResolver {
    /** [isDarkMode] is only consulted for [AppIconVariant.SYSTEM] -- Android has no dynamic
     * per-appearance icon mechanism like iOS, so "follow the device" has to mean "resolve to
     * Light or Dark right now, and re-resolve on every app start" rather than something that
     * updates live while the launcher is showing. */
    fun resolve(variant: AppIconVariant, isDarkMode: Boolean): ConcreteAppIcon = when (variant) {
        AppIconVariant.SYSTEM -> if (isDarkMode) ConcreteAppIcon.DARK else ConcreteAppIcon.LIGHT
        AppIconVariant.LIGHT -> ConcreteAppIcon.LIGHT
        AppIconVariant.DARK -> ConcreteAppIcon.DARK
        AppIconVariant.DISCO -> ConcreteAppIcon.DISCO
    }
}
