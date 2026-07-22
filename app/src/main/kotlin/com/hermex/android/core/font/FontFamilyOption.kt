package com.hermex.android.core.font

/**
 * A font selection — one of: system default, a named system typeface, or a downloadable Google Font.
 *
 * Stored as a flat [storageKey] string (readable, stable across model changes) so the DataStore
 * doesn't need to know about sealed classes. Parse with [fromStorageKey].
 */
sealed interface FontFamilyOption {
    val displayName: String
    val storageKey: String

    /** The platform's default typeface — resolves to [android.graphics.Typeface.DEFAULT]. */
    data object SystemDefault : FontFamilyOption {
        override val displayName = "System Default"
        override val storageKey = "system_default"
        override fun toString() = displayName
    }

    /** A named system typeface (e.g. "sans-serif", "serif", "monospace"). Resolves via
     * [android.graphics.Typeface.create]. */
    data class SystemFont(val androidName: String, override val displayName: String) : FontFamilyOption {
        override val storageKey = androidName
        override fun toString() = displayName
    }

    /** A downloadable Google Font (e.g. "Inter", "JetBrains Mono"). Resolved via Compose's
     * [androidx.compose.ui.text.googlefonts.GoogleFont] provider. A [categoryHint] is kept so the
     * picker can pre-filter if the Google Fonts API is consulted; it is informational only and
     * does not affect resolution. */
    data class GoogleFont(
        val name: String,
        /** Optional API-reported category — "monospace" vs. "sans-serif" / "serif" / "display" /
         * "handwriting". Used during autocomplete when a future API integration is wired in. */
        val categoryHint: String? = null,
    ) : FontFamilyOption {
        override val storageKey = "google:$name"
        override val displayName = name
        override fun toString() = "Google $name"
    }

    companion object {
        /** System options shown in the UI-font picker — deliberately excludes "monospace". */
        val UI_SYSTEM_OPTIONS: List<FontFamilyOption> = listOf(
            SystemDefault,
            SystemFont("sans-serif", "Default"),
            SystemFont("serif", "Serif"),
            SystemFont("sans-serif-light", "Light"),
            SystemFont("sans-serif-medium", "Medium"),
            SystemFont("sans-serif-thin", "Thin"),
        )

        /** System options shown in the monospace-font picker — only monospace variants. */
        val MONO_SYSTEM_OPTIONS: List<FontFamilyOption> = listOf(
            SystemDefault,
            SystemFont("monospace", "Monospace"),
        )

        /**
         * All system fonts available on the device (used when the user hasn't typed a Google
         * Font name and we show the defaults).
         */
        fun systemOptions(isMonoPicker: Boolean): List<FontFamilyOption> =
            if (isMonoPicker) MONO_SYSTEM_OPTIONS else UI_SYSTEM_OPTIONS

        /** Reconstruct from the storage key written by [storageKey]. */
        fun fromStorageKey(key: String): FontFamilyOption {
            if (key == "system_default") return SystemDefault
            if (key.startsWith("google:")) return GoogleFont(key.removePrefix("google:"))
            // Try matching a known system font; if unknown, return it as a SystemFont anyway
            // so the stored value round-trips correctly.
            val known = (UI_SYSTEM_OPTIONS + MONO_SYSTEM_OPTIONS).filterIsInstance<SystemFont>()
            val match = known.find { it.androidName == key }
            return match ?: SystemFont(key, key)
        }
    }
}
