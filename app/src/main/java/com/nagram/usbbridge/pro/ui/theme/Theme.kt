package com.nagram.usbbridge.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ProDark = darkColorScheme(
    primary = Color(0xFF38E07B),
    onPrimary = Color(0xFF04150A),
    secondary = Color(0xFF70A7FF),
    tertiary = Color(0xFF63E6FF),
    background = Color(0xFF05070A),
    surface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFF151B24),
    onBackground = Color(0xFFF2F5F8),
    onSurface = Color(0xFFF2F5F8),
    onSurfaceVariant = Color(0xFF9DA9B8),
    error = Color(0xFFFF6B6B)
)

@Composable
fun ShahadatProTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ProDark, content = content)
}
