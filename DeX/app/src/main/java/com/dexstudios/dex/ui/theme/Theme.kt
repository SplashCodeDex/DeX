package com.dexstudios.dex.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkSecondaryText
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightSecondaryText
)

@Composable
fun DeXTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // disabled to enforce the specific UI design
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
  val subtleRipple = androidx.compose.material3.RippleConfiguration(
      color = if (darkTheme) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black,
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
      androidx.compose.runtime.CompositionLocalProvider(
          androidx.compose.material3.LocalRippleConfiguration provides subtleRipple,
          content = content
      )
  }
}
