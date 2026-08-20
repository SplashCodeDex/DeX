package com.dexstudios.dex.core.designsystem.components.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable


import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

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
    val lensHeight: Dp = 24.dp,
    /** Lens refraction amount in [Dp] (0 disables the lens entirely). */
    val lensAmount: Dp = 48.dp,
    /** Whether to apply the iOS-style saturation boost (requires API 33+). */
    val vibrancyEnabled: Boolean = true,
    /** RGB color separation on the lens edge for a premium prismatic look. */
    val chromaticAberration: Boolean = true,
    /** Depth-based distortion on the lens for a stronger refracting look. */
    val depthEffect: Boolean = true,
    /**
     * Fraction (0..1) of the lens refraction active at rest; the remainder kicks
     * in on press. 0 = press-only refraction, 0.5 = half the lens always visible.
     */
    val restRefraction: Float = 0f,
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

    /**
     * Crisp glass tuned for top-bar icon buttons (avatar, search, ...). Half the
     * refraction is always visible so the buttons read as glass at rest, ramping
     * to full on press.
     */
    val IconButton: LiquidGlassConfig = LiquidGlassConfig(
        blurRadius = 5.dp,
        lensHeight = 16.dp,
        lensAmount = 30.dp,
        vibrancyEnabled = false,
        chromaticAberration = true,
        depthEffect = true,
        restRefraction = 0.5f,
        highlight = Highlight.Plain,
        shadowRadius = 6.dp,
        shadowColor = Color.Black.copy(alpha = 0.2f),
    )

    /**
     * Frosted pill glass for the floating bottom navigation bar, which sits over
     * scrolling content. Dialed-back shader load: blur halved, lens-only refraction
     * (no chromatic aberration or depth effect), tint for readability.
     */
    val NavBar: LiquidGlassConfig = LiquidGlassConfig(
        shape = CircleShape,
        blurRadius = 6.dp,
        lensHeight = 10.dp,
        lensAmount = 20.dp,
        vibrancyEnabled = false,
        chromaticAberration = false,
        depthEffect = false,
        surfaceTint = Color.White,
        surfaceTintAlpha = 0.12f,
        highlight = Highlight.Plain,
        shadowRadius = 12.dp,
        shadowColor = Color.Black.copy(alpha = 0.2f),
    )

    /**
     * Frosted heavy glass for dialogs/cards over a dimmed scene. Lens + prismatic
     * edge over the (static) dimmed backdrop — readable thanks to the white tint.
     */
    val Dialog: LiquidGlassConfig = LiquidGlassConfig(
        shape = RoundedCornerShape(48.dp),
        blurRadius = 14.dp,
        lensHeight = 12.dp,
        lensAmount = 26.dp,
        vibrancyEnabled = false,
        chromaticAberration = true,
        depthEffect = true,
        surfaceTint = Color.White,
        surfaceTintAlpha = 0.25f,
        highlight = Highlight.Plain,
        shadowRadius = 16.dp,
        shadowColor = Color.Black.copy(alpha = 0.3f),
    )

    val Frosted: LiquidGlassConfig = LiquidGlassConfig(
        shape = CircleShape,
        blurRadius = 12.dp,
        lensHeight = 40.dp,
        lensAmount = 60.dp,
        vibrancyEnabled = true,
        chromaticAberration = true,
        depthEffect = true,
        surfaceTint = Color.White,
        surfaceTintAlpha = 0.18f,
        highlight = Highlight.Plain,
        shadowRadius = 10.dp,
        shadowColor = Color.Black.copy(alpha = 0.25f),
        innerShadow = InnerShadow(radius = 4.dp),
    )

    /**
     * Dark, high-contrast glass for the expanded Dynamic Island look.
     * Overlaps content with a prominent dark tint and heavy blur.
     */
    val DynamicIsland: LiquidGlassConfig = LiquidGlassConfig(
        shape = RoundedCornerShape(48.dp),
        blurRadius = 16.dp,
        lensHeight = 20.dp,
        lensAmount = 40.dp,
        vibrancyEnabled = true,
        chromaticAberration = true,
        depthEffect = true,
        surfaceTint = Color.White,
        surfaceTintAlpha = 0.12f,
        highlight = Highlight.Ambient,
        shadowRadius = 24.dp,
        shadowColor = Color.Black.copy(alpha = 0.2f),
    )

    /**
     * Minimalist flat glass with no lens distortion or prismatic gradients.
     * Deep blur and subtle tint for a modern, clean look.
     */
    val Flat: LiquidGlassConfig = LiquidGlassConfig(
        shape = RoundedCornerShape(48.dp),
        blurRadius = 24.dp,
        lensHeight = 0.dp,
        lensAmount = 0.dp,
        vibrancyEnabled = false,
        chromaticAberration = false,
        depthEffect = false,
        surfaceTint = Color.White,
        surfaceTintAlpha = 0.12f,
        highlight = Highlight.Ambient,
        shadowRadius = 12.dp,
        shadowColor = Color.Black.copy(alpha = 0.15f),
    )

    /**
     * Interactive version of the flat glass, slightly more opaque for buttons.
     */
    val FlatInteractive: LiquidGlassConfig = LiquidGlassConfig(
        shape = CircleShape,
        blurRadius = 16.dp,
        lensHeight = 0.dp,
        lensAmount = 0.dp,
        vibrancyEnabled = false,
        chromaticAberration = false,
        depthEffect = false,
        surfaceTint = Color.White,
        surfaceTintAlpha = 0.15f,
        highlight = Highlight.Ambient,
        shadowRadius = 8.dp,
        shadowColor = Color.Black.copy(alpha = 0.2f),
    )
}

/**
 * 1:1 Parity DeX Liquid Glass Presets matching WPF desktop card and quick actions.
 */

object DeXGlassPresets {
    val DockCardDark: LiquidGlassConfig
        @Composable
        @ReadOnlyComposable
        get() = LiquidGlassConfig(
            shape = RoundedCornerShape(34.dp),
            blurRadius = 0.dp,
            lensHeight = 0.dp, // No glow/lens gradient
            lensAmount = 0.dp, // pure glass
            surfaceTint = MaterialTheme.colorScheme.surface,
            surfaceTintAlpha = 1.0f,
            highlight = Highlight.Ambient.copy(alpha = 0.0f),
            shadowRadius = 0.dp,
            shadowColor = Color.Transparent,
            innerShadow = null // no inset glow
        )

    val DockCardLight: LiquidGlassConfig
        @Composable
        @ReadOnlyComposable
        get() = LiquidGlassConfig(
            shape = RoundedCornerShape(34.dp),
            blurRadius = 0.dp,
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            surfaceTint = MaterialTheme.colorScheme.surface,
            surfaceTintAlpha = 1.0f,
            highlight = Highlight.Ambient.copy(alpha = 0.0f),
            shadowRadius = 0.dp,
            shadowColor = Color.Transparent,
            innerShadow = null
        )

    val DockCard: LiquidGlassConfig
        @Composable
        @ReadOnlyComposable
        get() = if (androidx.compose.foundation.isSystemInDarkTheme()) DockCardDark else DockCardLight

    val QuickActionDark: LiquidGlassConfig
        @Composable
        @ReadOnlyComposable
        get() = LiquidGlassConfig(
            shape = RoundedCornerShape(20.dp),
            blurRadius = 0.dp,
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
            surfaceTintAlpha = 1.0f,
            highlight = Highlight.Ambient.copy(alpha = 0.0f),
            shadowRadius = 0.dp,
            shadowColor = Color.Transparent
        )

    val QuickActionActive: LiquidGlassConfig
        @Composable
        @ReadOnlyComposable
        get() = LiquidGlassConfig(
            shape = RoundedCornerShape(20.dp),
            blurRadius = 0.dp,
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            surfaceTint = MaterialTheme.colorScheme.primary,
            surfaceTintAlpha = 1.0f,
            highlight = Highlight.Ambient.copy(alpha = 0.0f),
            shadowRadius = 0.dp,
            shadowColor = Color.Transparent
        )
}
