package com.questiontaker.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Premium medical/scientific themed color palette
private val LightPrimary = Color(0xFF00796B)        // Deep Teal
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightSecondary = Color(0xFF009688)      // Teal
private val LightBackground = Color(0xFFF4F6F6)     // Cool light gray/white
private val LightSurface = Color(0xFFFFFFFF)
private val LightOnSurface = Color(0xFF1C1B1F)
private val LightError = Color(0xFFB00020)

private val DarkPrimary = Color(0xFF4DB6AC)         // Soft Mint Teal
private val DarkOnPrimary = Color(0xFF003730)
private val DarkSecondary = Color(0xFF80CBC4)       // Light Teal Accent
private val DarkBackground = Color(0xFF0B131A)      // Deep slate black
private val DarkSurface = Color(0xFF15222E)         // Deep slate blue card surface
private val DarkOnSurface = Color(0xFFE2E8F0)       // Cool light gray text
private val DarkError = Color(0xFFCF6679)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    error = LightError
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    error = DarkError
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
