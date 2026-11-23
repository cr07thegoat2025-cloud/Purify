package com.purify.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Gold = Color(0xFFE9C46A)

private val PurifyColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1B1B1B),
    secondary = Color(0xFF5FB89A),
    background = Color(0xFF0B1F17),
    onBackground = Color(0xFFF2EFE6),
    surface = Color(0xFF10291E),
    onSurface = Color(0xFFF2EFE6),
    surfaceVariant = Color(0xFF1B3B2C),
    onSurfaceVariant = Color(0xFFB9C9BF),
    outline = Color(0xFF3E5E4E),
)

@Composable
fun PurifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PurifyColors, content = content)
}
