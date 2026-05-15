package com.example.cvproject.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val F1DarkColorScheme = darkColorScheme(
    primary = F1Colors.Red,
    onPrimary = F1Colors.TextPrimary,
    secondary = F1Colors.Blue,
    onSecondary = F1Colors.TextPrimary,
    tertiary = F1Colors.Amber,
    background = F1Colors.Background,
    onBackground = F1Colors.TextPrimary,
    surface = F1Colors.Surface,
    onSurface = F1Colors.TextPrimary,
    surfaceVariant = F1Colors.Surface,
    onSurfaceVariant = F1Colors.TextMuted,
)

private val F1LightColorScheme = lightColorScheme(
    primary = F1Colors.Red,
    onPrimary = F1Colors.TextPrimary,
    secondary = F1Colors.Blue,
    onSecondary = F1Colors.TextPrimary,
    tertiary = F1Colors.Amber,
    background = androidx.compose.ui.graphics.Color(0xFFF4F5F7),
    onBackground = androidx.compose.ui.graphics.Color(0xFF111111),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF111111),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE5E7EB),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF555555),
)

@Composable
fun CvprojectTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) F1DarkColorScheme else F1LightColorScheme,
        typography = Typography,
        content = content
    )
}
