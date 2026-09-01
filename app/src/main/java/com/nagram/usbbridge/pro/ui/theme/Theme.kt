package com.nagram.usbbridge.pro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

private val ProBlueWhite = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FF),
    onPrimaryContainer = Color(0xFF0B2F75),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5F6FF),
    onSecondaryContainer = Color(0xFF083B55),
    tertiary = Color(0xFF4F46E5),
    background = Color(0xFFF6F9FF),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF0F5FF),
    onSurfaceVariant = Color(0xFF667085),
    outline = Color(0xFFD8E2F1),
    outlineVariant = Color(0xFFE7EDF7),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val ProDark = darkColorScheme(
    primary = Color(0xFF77A7FF),
    onPrimary = Color(0xFF002F69),
    primaryContainer = Color(0xFF123A70),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFF74D1FF),
    onSecondary = Color(0xFF003548),
    background = Color(0xFF0E1420),
    onBackground = Color(0xFFF3F6FC),
    surface = Color(0xFF151D2A),
    onSurface = Color(0xFFF3F6FC),
    surfaceVariant = Color(0xFF202B3B),
    onSurfaceVariant = Color(0xFFB6C2D2),
    outline = Color(0xFF43536A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val ProAmoled = ProDark.copy(
    background = Color.Black,
    surface = Color(0xFF090D13),
    surfaceVariant = Color(0xFF111824)
)

@Composable
fun ShahadatProTheme(mode: AppThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }
    val colors = when {
        mode == AppThemeMode.AMOLED -> ProAmoled
        dark -> ProDark
        else -> ProBlueWhite
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
