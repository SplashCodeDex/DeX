package com.dexstudios.dex.ui.components.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
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
    val blurRadius: Dp = 1.dp,
    /** Lens refraction edge height in [Dp] (0 disables the lens entirely). */
    val lensHeight: Dp = 54.dp,
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
    /** Offset of the drop shadow. */
    val shadowOffset: DpOffset = DpOffset(0.dp, 8.dp / 6f),
    /** Optional inner shadow for depth (null = none). */
    val innerShadow: InnerShadow? = null,

    // Advanced shader parameters (mapped from JSON)
    val refFresnelRange: Float = 0f,
    val refFresnelHardness: Float = 0f,
    val refFresnelFactor: Float = 0f,
    val glareRange: Float = 0f,
    val glareHardness: Float = 0f,
    val glareFactor: Float = 0f,
    val glareAngle: Float = 0f
)

/**
 * Curated glass presets. `Default` matches the general crisp-glass recipe;
 * `Frosted` is the heavier, more matte look suited to dialogs/sheets.
 */
object LiquidGlassPresets {
    val Default: LiquidGlassConfig = LiquidGlassConfig()

    /**
     * Internal source for the Liquid Glass spec tuned to match
     * liquid-glass-2026-08-17T18-35-42.json
     */
    private val IconButtonDark = LiquidGlassConfig(
        blurRadius = 1.dp,
        lensHeight = 64.dp,
        lensAmount = 35.dp,
        vibrancyEnabled = false,
        chromaticAberration = false,
        depthEffect = true,
        restRefraction = 0.5f,
        surfaceTint = Color.Black,
        surfaceTintAlpha = 0.23f,
        highlight = Highlight(
            style = HighlightStyle.Default(
                angle = -52.82f,
                falloff = 20f / 10f // glareHardness 20 mapped to falloff
            ),
            alpha = 0.78f // glareFactor 78
        ),
        shadowRadius = 4.dp,
        innerShadow = null,
        refFresnelRange = 64.56f,
        refFresnelHardness = 21.58f,
        refFresnelFactor = 7.5f,
        glareRange = 35.63f,
        glareHardness = 20f,
        glareFactor = 78f,
        glareAngle = -52.82f
    )

    /**
     * Internal source for the expanded Liquid Glass spec.
     */
    private val DynamicIslandDark = IconButtonDark.copy(
        shape = RoundedCornerShape(48.dp),
        blurRadius = 4.dp,
        restRefraction = 1.0f
    )

    /**
     * Internal source for the Navigation Bar spec.
     */
    private val NavBarDark = IconButtonDark.copy(
        shape = CircleShape,
        blurRadius = 6.dp,
        restRefraction = 1.0f,
        shadowRadius = 12.dp,
        shadowColor = Color.Black.copy(alpha = 0.2f)
    )

    /**
     * Internal source for the Frosted spec (default for panels).
     */
    private val FrostedDark = IconButtonDark.copy(
        blurRadius = 12.dp,
        restRefraction = 1.0f
    )

    /**
     * Internal source for the Dialog spec.
     */
    private val DialogDark = IconButtonDark.copy(
        shape = RoundedCornerShape(48.dp),
        blurRadius = 16.dp,
        restRefraction = 1.0f,
        shadowRadius = 16.dp,
        shadowColor = Color.Black.copy(alpha = 0.3f)
    )

    /**
     * Theme-aware preset for top-bar icon buttons (avatar, search, ...).
     * In Dark mode, it applies a dark tint to ensure contrast.
     * In Light mode, it removes the tint for a clean glass look.
     */
    val IconButton: LiquidGlassConfig
        @Composable
        get() = if (isSystemInDarkTheme()) {
            IconButtonDark
        } else {
            IconButtonDark.copy(surfaceTintAlpha = 0f)
        }

    /**
     * Theme-aware preset for the floating bottom navigation bar, which sits over
     * scrolling content. In Dark mode, it applies a dark tint.
     * In Light mode, it removes the tint for a clean glass look.
     */
    val NavBar: LiquidGlassConfig
        @Composable
        get() = if (isSystemInDarkTheme()) {
            NavBarDark
        } else {
            NavBarDark.copy(surfaceTintAlpha = 0f)
        }

    /**
     * Theme-aware preset for the expanded Dynamic Island look.
     * In Dark mode, it applies a dark tint.
     * In Light mode, it removes the tint for a clean glass look.
     */
    val DynamicIsland: LiquidGlassConfig
        @Composable
        get() = if (isSystemInDarkTheme()) {
            DynamicIslandDark
        } else {
            DynamicIslandDark.copy(surfaceTintAlpha = 0f)
        }

    /**
     * Theme-aware preset for a heavier, more matte look suited to generic panels.
     */
    val Frosted: LiquidGlassConfig
        @Composable
        get() = if (isSystemInDarkTheme()) {
            FrostedDark
        } else {
            FrostedDark.copy(surfaceTintAlpha = 0f)
        }

    /**
     * Theme-aware preset for dialogs and cards over a dimmed scene.
     */
    val Dialog: LiquidGlassConfig
        @Composable
        get() = if (isSystemInDarkTheme()) {
            DialogDark
        } else {
            DialogDark.copy(surfaceTintAlpha = 0f)
        }

    /**
     * Minimalist flat glass with no lens distortion or prismatic gradients.
     * Deep blur and subtle tint for a modern, clean look.
     */
    val Flat: LiquidGlassConfig
        @Composable
        get() = if (isSystemInDarkTheme()) {
            LiquidGlassConfig(
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
                shadowColor = Color.Black.copy(alpha = 0.15f)
            )
        } else {
            LiquidGlassConfig(
                shape = RoundedCornerShape(48.dp),
                blurRadius = 24.dp,
                lensHeight = 0.dp,
                lensAmount = 0.dp,
                vibrancyEnabled = false,
                chromaticAberration = false,
                depthEffect = false,
                surfaceTint = Color.White,
                surfaceTintAlpha = 0f,
                highlight = Highlight.Ambient,
                shadowRadius = 12.dp,
                shadowColor = Color.Black.copy(alpha = 0.15f)
            )
        }

    /**
     * Interactive version of the flat glass, slightly more opaque for buttons.
     */
    val FlatInteractive: LiquidGlassConfig
        @Composable
        get() = if (isSystemInDarkTheme()) {
            LiquidGlassConfig(
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
                shadowColor = Color.Black.copy(alpha = 0.2f)
            )
        } else {
            LiquidGlassConfig(
                shape = CircleShape,
                blurRadius = 16.dp,
                lensHeight = 0.dp,
                lensAmount = 0.dp,
                vibrancyEnabled = false,
                chromaticAberration = false,
                depthEffect = false,
                surfaceTint = Color.White,
                surfaceTintAlpha = 0f,
                highlight = Highlight.Ambient,
                shadowRadius = 8.dp,
                shadowColor = Color.Black.copy(alpha = 0.2f)
            )
        }
}
