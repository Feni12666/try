package com.nagram.usbbridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AccentBlue = Color(0xFF69AEFF)
val AccentBlueDark = Color(0xFF1267D6)
val AccentMint = Color(0xFF48DCC6)
val AccentViolet = Color(0xFFA691FF)
val AppBackgroundDark = Color(0xFF060D14)
val AppSurfaceDark = Color(0xFF0E1822)
val AppSurfaceVariantDark = Color(0xFF152531)
val AppTextDark = Color(0xFFEDF8F6)
val AppMutedDark = Color(0xFF94A7AD)
val AppBackgroundLight = Color(0xFFF7FAFF)
val AppSurfaceLight = Color(0xFFFFFFFF)
val AppTextLight = Color(0xFF122033)

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color(0xFF002B50),
    primaryContainer = Color(0xFF123456),
    onPrimaryContainer = Color(0xFFD5E9FF),
    secondary = AccentViolet,
    onSecondary = Color(0xFF1E104C),
    secondaryContainer = Color(0xFF30275A),
    onSecondaryContainer = Color(0xFFE2D9FF),
    tertiary = AccentMint,
    background = AppBackgroundDark,
    onBackground = AppTextDark,
    surface = AppSurfaceDark,
    onSurface = AppTextDark,
    surfaceVariant = AppSurfaceVariantDark,
    onSurfaceVariant = AppMutedDark,
    outline = Color(0xFF38505A),
    error = Color(0xFFFF8D9A),
)

private val LightColors = lightColorScheme(
    primary = AccentBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8FF),
    onPrimaryContainer = Color(0xFF00315D),
    secondary = Color(0xFF6750A4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEFF),
    onSecondaryContainer = Color(0xFF25134F),
    tertiary = Color(0xFF007E70),
    background = AppBackgroundLight,
    onBackground = AppTextLight,
    surface = AppSurfaceLight,
    onSurface = AppTextLight,
    surfaceVariant = Color(0xFFEDF3FA),
    onSurfaceVariant = Color(0xFF526174),
    outline = Color(0xFF8492A5),
    error = Color(0xFFBA1A1A),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
)

val AppShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8),
    small = RoundedCornerShape(12),
    medium = RoundedCornerShape(18),
    large = RoundedCornerShape(24),
    extraLarge = RoundedCornerShape(30),
)

@Composable
fun UsbVideoManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
