package com.decmike.canonremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CanonRed = Color(0xFFD7263D)

private val Dark = darkColorScheme(
    primary = CanonRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C1019),
    onPrimaryContainer = Color(0xFFFFDADB),
    secondary = Color(0xFFE0B04A),
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    surface = Color(0xFF161616),
    surfaceVariant = Color(0xFF262626),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF262626),
    onSurface = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFFBDBDBD),
)

@Composable
fun CanonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}
