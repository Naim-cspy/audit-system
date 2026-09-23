package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SupermarketGreen,
    onPrimary = Color.White,
    primaryContainer = SupermarketGreenLight,
    onPrimaryContainer = SupermarketGreenDark,
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = AccentBlueLight,
    onSecondaryContainer = Color(0xFF01446B),
    tertiary = WarningAmber,
    onTertiary = Color.Black,
    error = CriticalRed,
    onError = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F4),
    onSurfaceVariant = TextSecondary,
    outline = BorderColor
)

@Composable
fun SupermarketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
