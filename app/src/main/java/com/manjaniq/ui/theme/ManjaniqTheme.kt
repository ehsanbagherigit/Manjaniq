// Package com.manjaniq.ui.theme provides theming for the application.
package com.manjaniq.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ManjaniqColors defines the color scheme for the Manjaniq application.
val ManjaniqColors = darkColorScheme(
    primary = Color(0xFF8250D9),
    onPrimary = Color.White,
    background = Color(0xFF080808),
    onBackground = Color(0xFFF5F3F8),
    surface = Color(0xFF15141A),
    onSurface = Color(0xFFF5F3F8),
    surfaceVariant = Color(0xFF15141A),
    onSurfaceVariant = Color(0xFFAAA6B4),
    outline = Color(0xFF24232B)
)

// ManjaniqTheme is the Compose theme wrapper used by MainActivity.
// (Before 2026-10-18 this file declared package com.manjaniq.ui and an
// `object ManjaniqTheme`, so MainActivity's import of
// com.manjaniq.ui.theme.ManjaniqTheme and its `ManjaniqTheme { ... }`
// call could not compile once the native build got far enough.)
@Composable
fun ManjaniqTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ManjaniqColors, content = content)
}
