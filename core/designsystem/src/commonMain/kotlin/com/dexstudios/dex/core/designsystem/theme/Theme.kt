package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.Backdrop

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkSecondaryText,
    error = Color(0xFFFF453A), // Kept from original desktop dark danger color for parity
    onError = DarkOnPrimary
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightSecondaryText,
    error = Color(0xFFFF3B30), // Kept from original desktop light danger color
    onError = LightOnPrimary
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // disabled to enforce the specific UI design
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val subtleRipple = androidx.compose.material3.RippleConfiguration(
        color = if (darkTheme) Color.White else Color.Black,
        rippleAlpha = androidx.compose.material.ripple.RippleAlpha(
            draggedAlpha = 0.02f,
            focusedAlpha = 0.02f,
            hoveredAlpha = 0.02f,
            pressedAlpha = 0.05f // Exceptionally subtle, mostly for glass
        )
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
    ) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalRippleConfiguration provides subtleRipple,
            content = content
        )
    }
}
