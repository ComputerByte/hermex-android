package com.hermex.android.core.storage

/** A user-selectable launcher icon, mirroring iOS's alternate-icon picker (`AppIconLight`,
 * `AppIconDark`, `AppIconDisco` in the iOS asset catalog). [SYSTEM] has no launcher icon of its
 * own -- Android has no equivalent of iOS's dynamic alternate-icon mechanism, so it's resolved to
 * [com.hermex.android.core.appicon.ConcreteAppIcon.LIGHT] or `.DARK` at reconcile time based on
 * the device's current dark-mode state (see `AppIconResolver`). */
enum class AppIconVariant(val displayName: String, val description: String) {
    SYSTEM("System", "Matches device appearance"),
    LIGHT("Light", "Always use the light icon"),
    DARK("Dark", "Always use the dark icon"),
    DISCO("Disco", "Always use the disco icon");

    companion object {
        /** Falls back to [SYSTEM] for anything that isn't an exact, current enum name -- covers
         * a never-saved preference, a corrupted value, and a name from a future/older app version
         * this build doesn't recognize. */
        fun fromStoredNameOrDefault(name: String?): AppIconVariant = entries.find { it.name == name } ?: SYSTEM
    }
}
