package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CustomDarkColorScheme = darkColorScheme(
    primary = CosmicCyan,
    secondary = CosmicAmber,
    tertiary = CosmicGreen,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = Color.White,
    error = CosmicRed
)

private val CustomLightColorScheme = lightColorScheme(
    primary = Color(0xFF00ACC1),
    secondary = CosmicAmber,
    tertiary = CosmicGreen,
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF1E232D),
    onSurface = Color(0xFF1E232D),
    surfaceVariant = Color(0xFFECEFF1),
    onSurfaceVariant = Color(0xFF1E232D),
    error = CosmicRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Force custom dark branding for immersive console experience
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CustomDarkColorScheme else CustomLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
