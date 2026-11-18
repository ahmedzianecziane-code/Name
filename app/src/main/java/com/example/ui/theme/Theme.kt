package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NeuralLensColorScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = ObsidianBg,
    primaryContainer = EmeraldDim.copy(alpha = 0.2f),
    onPrimaryContainer = EmeraldAccent,
    secondary = CyanAccent,
    onSecondary = ObsidianBg,
    secondaryContainer = CyanAccent.copy(alpha = 0.18f),
    onSecondaryContainer = CyanAccent,
    tertiary = AmberRayTrace,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    outlineVariant = GlassBorderBright,
    error = ErrorCoral,
    onError = ObsidianBg
)

@Composable
fun NeuralLensTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeuralLensColorScheme,
        typography = Typography,
        content = content
    )
}
