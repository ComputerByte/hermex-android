package com.hermex.android.core.storage

/** A user-selectable tint for the "Hermex" header text in the main session list's app bar --
 * purely a local appearance preference, unrelated to any server. [DEFAULT] preserves the app's
 * current appearance (the app bar's normal title color) rather than forcing a specific color. */
enum class HeaderLogoColor(val displayName: String) {
    DEFAULT("Default"),
    BLUE("Blue"),
    PURPLE("Purple"),
    GREEN("Green"),
    ORANGE("Orange"),
    RED("Red"),
    PINK("Pink"),
    MONO("Mono");

    companion object {
        /** Falls back to [DEFAULT] for anything that isn't an exact, current enum name -- covers
         * a never-saved preference, a corrupted value, and a name from a future/older app version
         * this build doesn't recognize. */
        fun fromStoredNameOrDefault(name: String?): HeaderLogoColor = entries.find { it.name == name } ?: DEFAULT
    }
}
