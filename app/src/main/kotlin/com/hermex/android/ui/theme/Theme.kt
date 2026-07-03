package com.hermex.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hermex.android.core.storage.HeaderLogoColor

// A deliberate palette instead of Compose's unstyled baseline purple (which is what made the
// app look like an undesigned default theme) -- a warm red accent on a near-black surface for
// dark mode, echoing the iOS app's dark/red branding without copying its assets.
// Internal (not private): reused by HeaderLogoColor.RED below, so the header-color option stays
// in sync with the app's actual brand color instead of duplicating the hex value.
internal val HermexRed = Color(0xFFE0473C)
private val HermexRedDark = Color(0xFFB6392F)

// Fixed swatches for the Header Logo Color setting -- chosen to read clearly against both the
// light and dark app-bar backgrounds above, since a user's choice here isn't theme-adaptive like
// HeaderLogoColor.DEFAULT (which instead resolves to the current title color at read time).
private val HeaderLogoBlue = Color(0xFF4A90D9)
private val HeaderLogoPurple = Color(0xFF9B59B6)
private val HeaderLogoGreen = Color(0xFF4CAF50)
private val HeaderLogoOrange = Color(0xFFFF9800)
private val HeaderLogoPink = Color(0xFFE91E8C)
private val HeaderLogoMono = Color(0xFF9E9E9E)

/** Resolves a [HeaderLogoColor] to an actual paintable [Color]. [HeaderLogoColor.DEFAULT] must
 * stay a `@Composable` read of the current title color (not a fixed constant) so it exactly
 * preserves today's appearance in both light and dark mode, rather than hardcoding one of them. */
@Composable
fun HeaderLogoColor.toComposeColor(): Color = when (this) {
    HeaderLogoColor.DEFAULT -> MaterialTheme.colorScheme.onSurface
    HeaderLogoColor.BLUE -> HeaderLogoBlue
    HeaderLogoColor.PURPLE -> HeaderLogoPurple
    HeaderLogoColor.GREEN -> HeaderLogoGreen
    HeaderLogoColor.ORANGE -> HeaderLogoOrange
    HeaderLogoColor.RED -> HermexRed
    HeaderLogoColor.PINK -> HeaderLogoPink
    HeaderLogoColor.MONO -> HeaderLogoMono
}

private val DarkColors = darkColorScheme(
    primary = HermexRed,
    onPrimary = Color.White,
    primaryContainer = HermexRedDark,
    onPrimaryContainer = Color.White,
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2C),
    onSurfaceVariant = Color(0xFFC7C7CC),
    surfaceContainerHighest = Color(0xFF2E2E30),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = HermexRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410001),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1B),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1B),
    surfaceVariant = Color(0xFFF2DEDB),
    onSurfaceVariant = Color(0xFF534341),
    surfaceContainerHighest = Color(0xFFECE0DE),
    error = Color(0xFFBA1A1A),
)

@Composable
fun HermexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
