package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.Backdrop

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val DarkColorScheme = darkColorScheme(
    primary = DeXColors.Dark.Primary,
    onPrimary = DeXColors.Dark.PrimaryText,
    background = DeXColors.Dark.Primary,
    surface = DeXColors.Dark.Primary,
    surfaceVariant = DeXColors.Dark.Accent,
    onBackground = DeXColors.Dark.PrimaryText,
    onSurface = DeXColors.Dark.PrimaryText,
    onSurfaceVariant = DeXColors.Dark.SecondaryText,
    secondary = DeXColors.Dark.Secondary,
    onSecondary = DeXColors.Dark.SecondaryForeground,
    error = DeXColors.Dark.Danger,
    onError = DeXColors.Dark.PrimaryText,
    outline = DeXColors.Dark.Accent,
    outlineVariant = DeXColors.Dark.SecondarySelected
)

val LightColorScheme = lightColorScheme(
    primary = DeXColors.Light.Primary,
    onPrimary = DeXColors.Light.PrimaryText,
    background = DeXColors.Light.Primary,
    surface = DeXColors.Light.Primary,
    surfaceVariant = DeXColors.Light.Accent,
    onBackground = DeXColors.Light.PrimaryText,
    onSurface = DeXColors.Light.PrimaryText,
    onSurfaceVariant = DeXColors.Light.SecondaryText,
    secondary = DeXColors.Light.Secondary,
    onSecondary = DeXColors.Light.SecondaryForeground,
    error = DeXColors.Light.Danger,
    onError = Color.White,
    outline = DeXColors.Light.Accent,
    outlineVariant = DeXColors.Light.SecondarySelected
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

object DeXTheme {
    val colors: DeXColorsAccessor
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) DarkColorsAccessor else LightColorsAccessor
}

interface DeXColorsAccessor {
    val primary: Color
    val accent: Color
    val primaryText: Color
    val secondaryText: Color
    val secondary: Color
    val secondaryForeground: Color
    val danger: Color
    val secondaryHover: Color
    val secondarySelected: Color
    val secondarySelectedHover: Color
    val secondarySelectedBorder: Color
}

private object DarkColorsAccessor : DeXColorsAccessor {
    override val primary: Color get() = DeXColors.Dark.Primary
    override val accent: Color get() = DeXColors.Dark.Accent
    override val primaryText: Color get() = DeXColors.Dark.PrimaryText
    override val secondaryText: Color get() = DeXColors.Dark.SecondaryText
    override val secondary: Color get() = DeXColors.Dark.Secondary
    override val secondaryForeground: Color get() = DeXColors.Dark.SecondaryForeground
    override val danger: Color get() = DeXColors.Dark.Danger
    override val secondaryHover: Color get() = DeXColors.Dark.SecondaryHover
    override val secondarySelected: Color get() = DeXColors.Dark.SecondarySelected
    override val secondarySelectedHover: Color get() = DeXColors.Dark.SecondarySelectedHover
    override val secondarySelectedBorder: Color get() = DeXColors.Dark.SecondarySelectedBorder
}

private object LightColorsAccessor : DeXColorsAccessor {
    override val primary: Color get() = DeXColors.Light.Primary
    override val accent: Color get() = DeXColors.Light.Accent
    override val primaryText: Color get() = DeXColors.Light.PrimaryText
    override val secondaryText: Color get() = DeXColors.Light.SecondaryText
    override val secondary: Color get() = DeXColors.Light.Secondary
    override val secondaryForeground: Color get() = DeXColors.Light.SecondaryForeground
    override val danger: Color get() = DeXColors.Light.Danger
    override val secondaryHover: Color get() = DeXColors.Light.SecondaryHover
    override val secondarySelected: Color get() = DeXColors.Light.SecondarySelected
    override val secondarySelectedHover: Color get() = DeXColors.Light.SecondarySelectedHover
    override val secondarySelectedBorder: Color get() = DeXColors.Light.SecondarySelectedBorder
}
