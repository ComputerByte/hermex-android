package com.hermex.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermex.android.core.storage.HeaderLogoColor

object HermexColors {
    val HermesGold = Color(0xFFFFD700)
    val AccentBlueDark = Color(0xFF0A84FF)
    val AccentBlueLight = Color(0xFF007AFF)
    val SuccessDark = Color(0xFF30D158)
    val SuccessLight = Color(0xFF34C759)
    val WarningDark = Color(0xFFFF9F0A)
    val WarningLight = Color(0xFFFF9500)
    val ErrorDark = Color(0xFFFF453A)
    val ErrorLight = Color(0xFFFF3B30)
    val DarkBackground = Color(0xFF000000)
    val DarkSecondaryBackground = Color(0xFF1C1C1E)
    val DarkSurfaceLow = Color(0xFF141416)
    val DarkSurfaceHigh = Color(0xFF242426)
    val DarkSurfaceHighest = Color(0xFF2C2C2E)
    val DarkLabel = Color(0xFFFFFFFF)
    val DarkSecondaryLabel = Color(0xFF98989F)
    val DarkTertiaryLabel = Color(0xFF636366)
    val LightTertiaryLabel = Color(0xFFC4C4C6)
    val DarkSeparator = Color(0xFF38383A)
    val UserBubbleDark = Color(0xFF48484A)
    val CodeBackgroundDark = Color(0xFF0A0D12)
}

/** Mirrors the design system's `tokens/typography.css` scale (itself mirrored from the iOS app's
 * Dynamic Type-inspired `Type.kt`) -- sizes/weights/line-heights only, no custom font family, so
 * this still resolves to the platform system font (Roboto) exactly like the Material3 default it
 * replaces. Only the 11 slots the token file actually defines are overridden; `displayLarge/
 * Medium/Small` (unused by this dense, mobile-first UI) keep Material3's stock defaults. */
private val HermexTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 37.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp),
)

object HermexRadii {
    val Accessory = 10.dp
    val Tool = 9.dp
    val Cell = 12.dp
    val SettingsCard = 18.dp
    val Dialog = 20.dp
    val Bubble = 20.dp
    val Composer = 22.dp
    val Code = 24.dp
}

/** Common spacing scale for new/restyled layouts. Existing screens keep their inline dp values
 * until they're touched by a restyle pass; this isn't meant to force a retrofit. */
object HermexSpacing {
    val XS = 4.dp
    val SM = 8.dp
    val MD = 12.dp
    val LG = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}

// Kept for the legacy Settings header-color option. It is not the primary action/accent color.
internal val HermexRed = Color(0xFFE0473C)

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
    // Primary/accent actions use the brand gold, black content for contrast.
    primary = HermexColors.HermesGold,
    onPrimary = Color.Black,
    primaryContainer = HermexColors.UserBubbleDark,
    onPrimaryContainer = HermexColors.DarkLabel,
    secondary = HermexColors.AccentBlueDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0A2E52),
    onSecondaryContainer = Color(0xFFB8D8FF),
    background = HermexColors.DarkBackground,
    onBackground = HermexColors.DarkLabel,
    surface = HermexColors.DarkSecondaryBackground,
    onSurface = HermexColors.DarkLabel,
    surfaceVariant = HermexColors.DarkSurfaceHighest,
    onSurfaceVariant = HermexColors.DarkSecondaryLabel,
    surfaceContainerLowest = HermexColors.DarkBackground,
    surfaceContainerLow = HermexColors.DarkSurfaceLow,
    surfaceContainer = HermexColors.DarkSecondaryBackground,
    surfaceContainerHigh = HermexColors.DarkSurfaceHigh,
    surfaceContainerHighest = HermexColors.DarkSurfaceHighest,
    outline = HermexColors.DarkSeparator,
    outlineVariant = Color(0xFF2C2C2E),
    error = HermexColors.ErrorDark,
    errorContainer = Color(0xFF3A1210),
    onErrorContainer = Color(0xFFFFB3AD),
)

private val LightColors = lightColorScheme(
    primary = HermexColors.HermesGold,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFF2F2F7),
    onPrimaryContainer = Color.Black,
    secondary = HermexColors.AccentBlueLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EEFF),
    onSecondaryContainer = Color(0xFF00325C),
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF2F2F7),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF8A8A8E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFECECF1),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    error = HermexColors.ErrorLight,
    errorContainer = Color(0xFFFFE9E7),
    onErrorContainer = Color(0xFF7F1D16),
)

@Composable
fun HermexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HermexTypography,
        content = content,
    )
}
