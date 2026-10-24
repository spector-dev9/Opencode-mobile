package io.github.spectordev9.opencode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BrandBlue = Color(0xFF4C7DFF)
private val BrandBlueDark = Color(0xFF9DB4FF)
private val BrandTeal = Color(0xFF006A6A)
private val BrandTealLight = Color(0xFF7FD7D4)
private val BrandAmber = Color(0xFFB45F00)
private val BrandAmberLight = Color(0xFFFFB86B)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF0B1B4D),
    primaryContainer = Color(0xFF263C78),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = BrandTealLight,
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF004F4F),
    onSecondaryContainer = Color(0xFFB8F3F0),
    tertiary = BrandAmberLight,
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6B3D00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111820),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF202A35),
    onSurfaceVariant = Color(0xFFB8C3D1),
    outline = Color(0xFF748090),
    outlineVariant = Color(0xFF354150),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BD8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8F3F0),
    onSecondaryContainer = Color(0xFF002020),
    tertiary = BrandAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF171C22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171C22),
    surfaceVariant = Color(0xFFE8EDF4),
    onSurfaceVariant = Color(0xFF414A56),
    outline = Color(0xFF717A88),
    outlineVariant = Color(0xFFC1C8D3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val OpenCodeTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

val CodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)

@Composable
fun OpenCodeMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OpenCodeTypography,
        content = content,
    )
}
