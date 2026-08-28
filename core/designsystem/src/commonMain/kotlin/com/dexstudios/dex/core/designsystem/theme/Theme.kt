package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = DarkText,
    secondary = DarkPrimary,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkText,
    tertiary = DarkPrimary,
    onTertiary = DarkOnPrimary,
    tertiaryContainer = DarkSurfaceVariant,
    onTertiaryContainer = DarkText,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondaryText,
    surfaceTint = DarkPrimary,
    outline = Color.White.copy(alpha = 0.55f),
    outlineVariant = DarkSurfaceVariant,
    inverseSurface = Color.White,
    inverseOnSurface = DarkBackground,
    inversePrimary = DarkOnPrimary,
    surfaceDim = DarkBackground,
    surfaceBright = DarkSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceVariant,
    error = DarkError,
    onError = Color.Black,
    errorContainer = DarkError.copy(alpha = 0.2f),
    onErrorContainer = DarkError,
    scrim = Color.Black,
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = LightText,
    secondary = LightPrimary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightText,
    tertiary = LightPrimary,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightSurfaceVariant,
    onTertiaryContainer = LightText,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightSecondaryText,
    surfaceTint = LightPrimary,
    outline = LightSecondaryText.copy(alpha = 0.55f),
    outlineVariant = LightSecondaryText.copy(alpha = 0.28f),
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkText,
    inversePrimary = DarkPrimary,
    surfaceDim = LightBackground,
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSurface,
    error = LightError,
    onError = Color.White,
    errorContainer = LightError.copy(alpha = 0.15f),
    onErrorContainer = Color(0xFFB3261E),
    scrim = Color.Black,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // disabled to enforce the specific UI design
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val subtleRipple = RippleConfiguration(
        // Android parity: raw black ripples in light, white in dark.
        color = if (darkTheme) Color.White else Color.Black,
        rippleAlpha = RippleAlpha(
            draggedAlpha = 0.02f,
            focusedAlpha = 0.02f,
            hoveredAlpha = 0.02f,
            pressedAlpha = 0.05f, // Exceptionally subtle, mostly for glass
        ),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides subtleRipple,
            content = content,
        )
    }
}
