package com.nagram.usbbridge.pro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Final Design #8 direction: premium blue + white, clean surfaces, restrained accent use.
 * The app intentionally stays light/bright for this milestone so the approved visual language
 * remains consistent across devices instead of being replaced by a dark/neon fallback.
 */
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
    onError = Color.White,
    errorContainer = Color(0xFFFFE8E8),
    onErrorContainer = Color(0xFF7F1D1D)
)

@Composable
fun ShahadatProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ProBlueWhite,
        content = content
    )
}
