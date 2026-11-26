package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ZoyaDarkColorScheme = darkColorScheme(
    primary = ZoyaNeonPurple,
    onPrimary = ZoyaObsidian,
    primaryContainer = ZoyaDeepPurpleContainer,
    onPrimaryContainer = ZoyaBrightViolet,
    secondary = ZoyaElectricBlue,
    onSecondary = ZoyaObsidian,
    secondaryContainer = ZoyaDeepBlueContainer,
    onSecondaryContainer = ZoyaNeonCyan,
    tertiary = ZoyaNeonPink,
    onTertiary = ZoyaObsidian,
    background = ZoyaObsidian,
    onBackground = ZoyaTextPrimary,
    surface = ZoyaDeepSurface,
    onSurface = ZoyaTextPrimary,
    surfaceVariant = ZoyaCardSurface,
    onSurfaceVariant = ZoyaTextSecondary,
    surfaceContainerHighest = ZoyaElevatedSurface,
    outline = ZoyaBorderSubtle,
    outlineVariant = ZoyaBorderSubtle.copy(alpha = 0.5f),
    error = ZoyaCoralError,
    onError = ZoyaObsidian
)

private val ZoyaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ZoyaAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ZoyaDarkColorScheme,
        typography = ZoyaTypography,
        shapes = ZoyaShapes,
        content = content
    )
}
