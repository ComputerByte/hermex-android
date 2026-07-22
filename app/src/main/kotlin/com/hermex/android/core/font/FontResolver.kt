package com.hermex.android.core.font

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Resolves a stored font-family key to a Compose [FontFamily] applicable for **UI text**
 * (all Material typography slots). Falls back to [FontFamily.Default].
 *
 * - [FontFamilyOption.SystemDefault] → [FontFamily.Default]
 * - [FontFamilyOption.SystemFont] → [DeviceFontFamilyName]-based [FontFamily]
 * - [FontFamilyOption.GoogleFont] → attempts [DeviceFontFamilyName] lookup; falls back to [FontFamily.Default]
 */
@Composable
fun resolveUiFontFamily(fontKey: String): FontFamily {
    val option = FontFamilyOption.fromStorageKey(fontKey)
    return when (option) {
        is FontFamilyOption.SystemDefault -> FontFamily.Default
        is FontFamilyOption.SystemFont -> systemFontFamily(option)
        is FontFamilyOption.GoogleFont -> resolveGoogleFontFamily(option)
    }
}

/**
 * Resolves a stored font-family key to a Compose [FontFamily] for **monospace / code** text.
 * Falls back to [FontFamily.Monospace].
 *
 * - [FontFamilyOption.SystemDefault] → [FontFamily.Monospace]
 * - [FontFamilyOption.SystemFont] → [DeviceFontFamilyName]-based [FontFamily]
 * - [FontFamilyOption.GoogleFont] → same as [resolveUiFontFamily] for the named font
 */
@Composable
fun resolveMonospaceFontFamily(fontKey: String): FontFamily {
    val option = FontFamilyOption.fromStorageKey(fontKey)
    return when (option) {
        is FontFamilyOption.SystemDefault -> FontFamily.Monospace
        is FontFamilyOption.SystemFont -> systemFontFamily(option)
        is FontFamilyOption.GoogleFont -> resolveGoogleFontFamily(option)
    }
}

private fun systemFontFamily(font: FontFamilyOption.SystemFont): FontFamily {
    return FontFamily(
        Font(
            familyName = DeviceFontFamilyName(font.androidName),
            weight = FontWeight.Normal,
        ),
    )
}

// ── Google Font resolution ─────────────────────────────────────────────────────
//
// Google Fonts are resolved by name using the system typeface lookup.
// On Android 8+ (API 26+), Typeface.create(familyName, style) can resolve
// downloadable fonts that have been cached by the Google Play Services
// font provider.  This works for fonts already downloaded on the device.
//
// For fonts not yet cached, the first composition with a Google Font name
// triggers the system to download and cache it (via
// FontRequest / FontsContractCompat), and subsequent compositions will
// resolve successfully.
//
// NOTE: This is not a Compose-specific API — it uses the platform
// Typeface.create which is backed by the same downloadable fonts service.
// No separate Compose googlefonts artifact is needed.

/** Lightweight cache so repeated lookups don't hit the system on each recomposition. */
private val googleFontCache = mutableMapOf<String, Typeface?>()

private fun resolveGoogleFontFamily(font: FontFamilyOption.GoogleFont): FontFamily {
    val cached = googleFontCache.getOrPut(font.name) {
        try {
            Typeface.create(font.name, Typeface.NORMAL)
        } catch (_: Exception) {
            null
        }
    }
    if (cached != null) {
        return FontFamily(
            Font(
                familyName = DeviceFontFamilyName(font.name),
                weight = FontWeight.Normal,
            ),
        )
    }
    // Font not available yet — fall back gracefully.
    return FontFamily.Default
}

/**
 * Best-effort resolution of a [FontFamily] to an Android [Typeface] for use
 * in Markwon's AndroidView code-block rendering.
 *
 * - [FontFamily.Default] / [FontFamily.SansSerif] → null (no override needed)
 * - [FontFamily.Monospace] → [Typeface.MONOSPACE]
 * - [FontFamily.Serif] → [Typeface.SERIF]
 * - Anything else → [Typeface.MONOSPACE] as a safe fallback so code blocks
 *   remain visually distinct from prose.
 */
fun resolveAndroidTypeface(fontFamily: FontFamily): Typeface? {
    return when {
        fontFamily === FontFamily.Default -> null
        fontFamily === FontFamily.Monospace -> Typeface.MONOSPACE
        fontFamily === FontFamily.SansSerif -> null
        fontFamily === FontFamily.Serif -> Typeface.SERIF
        else -> Typeface.MONOSPACE
    }
}
