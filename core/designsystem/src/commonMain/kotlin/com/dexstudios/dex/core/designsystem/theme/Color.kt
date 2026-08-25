package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// DeX Identity Seeds — 1:1 with the Android app's palette
// (DeX/app ui/theme/Color.kt). Every scheme role below derives from these
// seven tokens per mode; no component may hardcode a raw hex that belongs
// to this palette.
// ============================================================================

// ---- Light seeds ----
val LightBackground = Color(0xFFDAD9DD)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE0E2EC)
val LightPrimary = Color(0xFF000000)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightText = Color(0xFF1A1C1E)
val LightSecondaryText = Color(0xFF44474E)

// ---- Dark seeds ----
val DarkBackground = Color(0xFF111318)
val DarkSurface = Color(0xFF1E1E20)
val DarkSurfaceVariant = Color(0xFF2F3033)
val DarkPrimary = Color(0xFFFFFFFF)
val DarkOnPrimary = Color(0xFF000000)
val DarkText = Color(0xFFE3E2E6)
val DarkSecondaryText = Color(0xFFC4C6CF)

// Ambient smoke plumes (main background haze) — user-tuned Android tones.
val SmokePurple = Color(0xC6FF8BEA)
val SmokeViolet = Color(0xFFA226FF)
val SmokePink = Color(0xFF673AB7)

// ============================================================================
// Light Theme Roles — monochrome ink identity on the warm-gray canvas.
// The Android app paints every CTA black-on-white, so primary/secondary/
// tertiary are one shared black accent; containers derive from SurfaceVariant
// and every raised layer stays pure white.
// ============================================================================

val LightPrimaryContainer = LightSurfaceVariant // #E0E2EC
val LightOnPrimaryContainer = LightText // #1A1C1E

val LightSecondary = LightPrimary
val LightOnSecondary = LightOnPrimary
val LightSecondaryContainer = LightSurfaceVariant
val LightOnSecondaryContainer = LightText

val LightTertiary = LightPrimary
val LightOnTertiary = LightOnPrimary
val LightTertiaryContainer = LightSurfaceVariant
val LightOnTertiaryContainer = LightText

val LightOnBackground = LightText // #1A1C1E
val LightOnSurface = LightText // #1A1C1E
val LightOnSurfaceVariant = LightSecondaryText // #44474E

val LightOutline = LightSecondaryText.copy(alpha = 0.55f)
val LightOutlineVariant = LightSecondaryText.copy(alpha = 0.28f)

val LightInverseSurface = DarkSurface // #1E1E20
val LightInverseOnSurface = DarkText // #E3E2E6
val LightInversePrimary = DarkPrimary // #FFFFFF

val LightSurfaceDim = LightBackground // #DAD9DD
val LightSurfaceBright = LightSurface // #FFFFFF
val LightSurfaceContainerLowest = LightSurface // raised layer stays white
val LightSurfaceContainerLow = LightSurface
val LightSurfaceContainer = LightSurface
val LightSurfaceContainerHigh = LightSurface
val LightSurfaceContainerHighest = LightSurface

// Danger reds kept from the desktop light palette (semantic, not brand).
val LightError = Color(0xFFFF3B30)
val LightOnError = Color.White
val LightErrorContainer = Color(0xFFFF3B30).copy(alpha = 0.15f)
val LightOnErrorContainer = Color(0xFFB3261E)

// ============================================================================
// Dark Theme Roles — near-black neutrals with a white primary accent,
// mirroring the approved Android dark identity. The container ladder maps
// onto the same three Android surface tones instead of inventing new steps.
// ============================================================================

val DarkPrimaryContainer = DarkSurfaceVariant // #2F3033
val DarkOnPrimaryContainer = DarkText // #E3E2E6

val DarkSecondary = DarkPrimary
val DarkOnSecondary = DarkOnPrimary
val DarkSecondaryContainer = DarkSurfaceVariant
val DarkOnSecondaryContainer = DarkText

val DarkTertiary = DarkPrimary
val DarkOnTertiary = DarkOnPrimary
val DarkTertiaryContainer = DarkSurfaceVariant
val DarkOnTertiaryContainer = DarkText

val DarkOnBackground = DarkText // #E3E2E6
val DarkOnSurface = DarkText // #E3E2E6
val DarkOnSurfaceVariant = DarkSecondaryText // #C4C6CF

val DarkOutline = Color.White.copy(alpha = 0.55f)

// Solid hairline tone: borders/dividers read exactly like the Android dark
// surfaceVariant step (#2F3033).
val DarkOutlineVariant = DarkSurfaceVariant // #2F3033

val DarkInverseSurface = Color.White
val DarkInverseOnSurface = DarkBackground // #111318
val DarkInversePrimary = DarkOnPrimary // #000000

val DarkSurfaceDim = DarkBackground // #111318
val DarkSurfaceBright = DarkSurfaceVariant // #2F3033
val DarkSurfaceContainerLowest = DarkBackground // #111318
val DarkSurfaceContainerLow = DarkSurface // #1E1E20
val DarkSurfaceContainer = DarkSurface // #1E1E20
val DarkSurfaceContainerHigh = DarkSurfaceVariant // #2F3033
val DarkSurfaceContainerHighest = DarkSurfaceVariant // #2F3033

// Danger reds kept from the desktop dark palette (semantic, not brand).
val DarkError = Color(0xFFFF453A)
val DarkOnError = Color.Black
val DarkErrorContainer = Color(0xFFFF453A).copy(alpha = 0.2f)
val DarkOnErrorContainer = Color(0xFFFF453A)

// Glass Spec (No glow, pure glass)
const val GlassSurfaceAlpha = 0.85f
