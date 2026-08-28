package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// DeX Identity Seeds — 1:1 with the Android app's palette (DeX/app ui/theme/Color.kt).
// Every theme role in Theme.kt derives directly from these core seed tokens.
// ============================================================================

// ---- Light Mode Seeds ----
val LightBackground = Color(0xFFDAD9DD)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F4FF) // Powers all resting buttons & search pill in Light Mode
val LightPrimary = Color(0xFF000000)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightText = Color(0xFF1A1C1E)
val LightSecondaryText = Color(0xFF44474E)
val LightError = Color(0xFFFF3B30)

// ---- Dark Mode Seeds ----
val DarkBackground = Color(0xFF111318)
val DarkSurface = Color(0xFF1E1E20)
val DarkSurfaceVariant = Color(0xFF2F3033) // Powers all resting buttons & search pill in Dark Mode
val DarkPrimary = Color(0xFFFFFFFF)
val DarkOnPrimary = Color(0xFF000000)
val DarkText = Color(0xFFE3E2E6)
val DarkSecondaryText = Color(0xFFC4C6CF)
val DarkError = Color(0xFFFF453A)

// ---- Ambient Smoke Plumes (Background haze) ----
val SmokePurple = Color(0xC6FF8BEA)
val SmokeViolet = Color(0xFFA226FF)
val SmokePink = Color(0xFF673AB7)
