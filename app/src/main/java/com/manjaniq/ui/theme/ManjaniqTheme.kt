package com.manjaniq.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ManjaniqColors = darkColorScheme(
    primary = ManjaniqPurple,
    onPrimary = Color.White,
    primaryContainer = ManjaniqPurpleDeep,
    onPrimaryContainer = ManjaniqPurpleSoft,
    secondary = RstaGold,
    onSecondary = Color(0xFF1A1200),
    secondaryContainer = RstaGoldSurface,
    onSecondaryContainer = RstaGoldSoft,
    tertiary = AetherCyan,
    onTertiary = Color(0xFF001F24),
    tertiaryContainer = AetherSurface,
    onTertiaryContainer = AetherCyanSoft,
    background = ManjaniqBackground,
    onBackground = ManjaniqOnBackground,
    surface = ManjaniqSurface,
    onSurface = ManjaniqOnSurface,
    surfaceVariant = ManjaniqSurfaceElevated,
    onSurfaceVariant = ManjaniqOnSurfaceVariant,
    surfaceDim = ManjaniqBackgroundSecondary,
    outline = ManjaniqBorder,
    outlineVariant = ManjaniqBorder,
    error = StatusError,
    onError = Color.White,
    errorContainer = Color(0xFF3B1210),
    onErrorContainer = Color(0xFFFFDAD4)
)

val ManjaniqShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val ManjaniqTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun ManjaniqTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ManjaniqColors,
        typography = ManjaniqTypography,
        shapes = ManjaniqShapes,
        content = content
    )
}
