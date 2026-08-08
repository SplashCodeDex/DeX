package com.example.dex.ui.components.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.Shadow

/**
 * Central, reusable configuration for a liquid glass surface.
 *
 * Every value is overridable by the caller so glass can be reused and tuned
 * per-component without scattering magic numbers. Defaults produce a crisp,
 * glossy "button" glass look.
 */
data class LiquidGlassConfig(
    /** Outline of the glass surface (shape lambda in `drawBackdrop` reads this). */
    val shape: Shape = CircleShape,
    /** Gaussian blur radius in [Dp] — small values keep the glass crisp. */
    val blurRadius: Dp = 3.dp,
    /** Lens refraction edge height in [Dp] (0 disables the lens entirely). */
    val lensHeight: Dp = 20.dp,
    /** Lens refraction amount in [Dp] (0 disables the lens entirely). */
    val lensAmount: Dp = 40.dp,
    /** Whether to apply the iOS-style saturation boost (requires API 33+). */
    val vibrancyEnabled: Boolean = true,
    /** RGB color separation on the lens edge for a premium prismatic look. */
    val chromaticAberration: Boolean = false,
    /** Optional surface tint overlaid on the glass (set [surfaceTintAlpha] to 0 to disable). */
    val surfaceTint: Color = Color.White,
    /** Alpha of the surface tint overlay (0..1). */
    val surfaceTintAlpha: Float = 0.12f,
    /** Highlight preset; each preset is a data class so `.copy(...)` works for animation. */
    val highlight: Highlight = Highlight.Ambient,
    /** Drop shadow behind the glass. */
    val shadowRadius: Dp = 8.dp,
    val shadowColor: Color = Color.Black.copy(alpha = 0.2f),
    /** Optional inner shadow for depth (null = none). */
    val innerShadow: InnerShadow? = null,
)

/**
 * Curated glass presets. `Default` matches the general crisp-glass recipe;
 * `Frosted` is the heavier, more matte look suited to dialogs/sheets.
 */
object LiquidGlassPresets {
    val Default: LiquidGlassConfig = LiquidGlassConfig()

    /** Crisp, glossy glass tuned for top-bar icon buttons (avatar, search, ...). */
    val IconButton: LiquidGlassConfig = LiquidGlassConfig(
        blurRadius = 2.dp,
        lensHeight = 16.dp,
        lensAmount = 32.dp,
        chromaticAberration = true
    )

    val Frosted: LiquidGlassConfig = LiquidGlassConfig(
        shape = CircleShape,
        blurRadius = 12.dp,
        lensHeight = 36.dp,
        lensAmount = 64.dp,
        vibrancyEnabled = true,
        chromaticAberration = false,
        surfaceTint = Color.White,
        surfaceTintAlpha = 0.18f,
        highlight = Highlight.Plain,
        shadowRadius = 10.dp,
        shadowColor = Color.Black.copy(alpha = 0.25f),
        innerShadow = InnerShadow(radius = 4.dp),
    )
}
