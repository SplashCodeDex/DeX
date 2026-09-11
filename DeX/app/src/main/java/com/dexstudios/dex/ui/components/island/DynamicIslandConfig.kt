package com.dexstudios.dex.ui.components.island

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassShadowProperties

/**
 * Bounds and layout dimensions configuration for dynamic morphing surfaces.
 *
 * @param collapsedWidth Width when collapsed at rest.
 * @param collapsedHeight Height when collapsed at rest.
 * @param expandedWidth Width when expanded. Use [Dp.Unspecified] to stretch dynamically to container width.
 * @param expandedHeight Height when expanded.
 */
data class DynamicDimensions(
    val collapsedWidth: Dp = 56.dp,
    val collapsedHeight: Dp = 56.dp,
    val compactWidth: Dp = collapsedWidth,
    val compactHeight: Dp = collapsedHeight,
    val expandedWidth: Dp = 260.dp,
    val expandedHeight: Dp = 56.dp,
    val fullIslandWidth: Dp = Dp.Unspecified,
    val fullIslandHeight: Dp = 140.dp,
) {
    companion object {
        /** Default dimensions for stadium pill buttons. */
        val PillDefault = DynamicDimensions(
            collapsedWidth = 56.dp,
            collapsedHeight = 56.dp,
            compactWidth = 56.dp,
            compactHeight = 56.dp,
            expandedWidth = 260.dp,
            expandedHeight = 56.dp,
        )

        /** Default dimensions for dynamic island card surfaces. */
        val IslandDefault = DynamicDimensions(
            collapsedWidth = 56.dp,
            collapsedHeight = 56.dp,
            compactWidth = 56.dp,
            compactHeight = 56.dp,
            expandedWidth = Dp.Unspecified,
            expandedHeight = 160.dp,
        )

        /** Default dimensions for compact action pills (e.g. History, Devices, collapsed active mode navbar). */
        val CompactPill = DynamicDimensions(
            collapsedWidth = 56.dp,
            collapsedHeight = 56.dp,
            compactWidth = 56.dp,
            compactHeight = 56.dp,
            expandedWidth = 130.dp,
            expandedHeight = 56.dp,
        )

        /** Default dimensions for profile identity stadium pill. */
        val ProfilePill = DynamicDimensions(
            collapsedWidth = 56.dp,
            collapsedHeight = 56.dp,
            compactWidth = 56.dp,
            compactHeight = 56.dp,
            expandedWidth = 210.dp,
            expandedHeight = 56.dp,
        )

        /** Full island resting card surface (e.g. Profile stage 2). */
        val FullIsland = DynamicDimensions(
            collapsedWidth = 56.dp,
            collapsedHeight = 56.dp,
            compactWidth = 56.dp,
            compactHeight = 56.dp,
            expandedWidth = Dp.Unspecified,
            expandedHeight = 140.dp,
        )

        /** Expanded island card surface during active file transfers. */
        val TransferIsland = DynamicDimensions(
            collapsedWidth = 56.dp,
            collapsedHeight = 56.dp,
            compactWidth = 56.dp,
            compactHeight = 56.dp,
            expandedWidth = Dp.Unspecified,
            expandedHeight = 180.dp,
        )

        /** Default dimensions for the selected items counter action pill (collapsed to zero at rest). */
        val SelectionCounterPill = DynamicDimensions(
            collapsedWidth = 0.dp,
            collapsedHeight = 56.dp,
            compactWidth = 56.dp,
            compactHeight = 56.dp,
            expandedWidth = 180.dp,
            expandedHeight = 56.dp,
        )
    }
}

/**
 * 3-Tier spatial regulation stage for Dynamic Pill Buttons (Apple Dynamic Island pattern).
 *
 * - [Collapsed]: Resting state (e.g. 0.dp hidden at rest, or 56.dp circular icon).
 * - [Compact]: Regulated auxiliary state (56.dp circular capsule with icon + badge when sibling has focus).
 * - [Expanded]: Full interactive stadium pill (160.dp - 260.dp with full actions and dismiss button).
 */
enum class DynamicPillStage {
    Collapsed,
    Compact,
    Expanded,
    FullIsland;

    val isExpanded: Boolean get() = this == Expanded || this == FullIsland
    val isVisible: Boolean get() = this != Collapsed
}

/**
 * Organic spring kinematics configuration for bounds morphing (expansion and collapse).
 *
 * Encapsulates Apple-style direction-aware dual damping:
 * - [expandDampingRatio]: Target damping ratio when opening (overshoot spring).
 * - [collapseDampingRatio]: Target damping ratio when closing (cleaner settle).
 * - [stiffness]: Response speed and elastic force.
 * - [springSpec]: Optional explicit animation spec override.
 */
data class DynamicMotionConfig(
    val expandDampingRatio: Float = 0.5f,
    val collapseDampingRatio: Float = 0.56f,
    val stiffness: Float = 170f,
    val springSpec: AnimationSpec<Dp>? = null,
) {
    /** Resolves active direction-aware spring specification based on [isExpanded]. */
    fun resolveSpringSpec(isExpanded: Boolean): AnimationSpec<Dp> {
        return springSpec ?: spring(
            dampingRatio = if (isExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        )
    }

    /** Direction-aware generic spring spec for any type (Float, Dp, Color, Int, etc.). */
    fun <T> springSpec(isExpanded: Boolean): AnimationSpec<T> {
        return spring(
            dampingRatio = if (isExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        )
    }

    /** Standard expand spring spec. */
    fun <T> expandSpec(): AnimationSpec<T> {
        return spring(
            dampingRatio = expandDampingRatio,
            stiffness = stiffness
        )
    }

    /** Standard collapse spring spec. */
    fun <T> collapseSpec(): AnimationSpec<T> {
        return spring(
            dampingRatio = collapseDampingRatio,
            stiffness = stiffness
        )
    }

    companion object {
        /** CodeDeX tuned signature fluid overshoot kinematics. */
        val Default = DynamicMotionConfig()

        /** Playful high-bouncy kinematics for expressive micro-interactions. */
        val Bouncy = DynamicMotionConfig(
            expandDampingRatio = 0.50f,
            collapseDampingRatio = 0.65f,
            stiffness = 350f
        )

        /** Snappy responsive kinematics with minimal overshoot for productivity surfaces. */
        val Snappy = DynamicMotionConfig(
            expandDampingRatio = 0.75f,
            collapseDampingRatio = 0.85f,
            stiffness = 600f
        )
    }
}

/**
 * Tactile touch, press deformation, and drag-tracking physics configuration.
 *
 * @param enabled Whether bubble fluidity physics are enabled.
 * @param pressScale Squish (< 1.0f) or swell (> 1.0f) scale multiplier on press.
 * @param pullFactor Finger drag displacement tracking factor.
 * @param elasticity Jelly stretch rebound and harmonic oscillation factor.
 * @param scalePressSpeed Response speed of touch-down scaling.
 * @param scalePressDamping Smoothness and damping of touch-down parallax scaling.
 * @param scaleSettleSpeed Release-to-settle spring speed while drag pull oscillation stays locked.
 */
data class DynamicFluidityConfig(
    val enabled: Boolean = true,
    val pressScale: Float = 1.08f,
    val pullFactor: Float = 0.14f,
    val elasticity: Float = 0.13f,
    val scalePressSpeed: Float = 288f,
    val scalePressDamping: Float = 0.35f,
    val scaleSettleSpeed: Float = 300f,
) {
    companion object {
        /** CodeDeX tuned authentic tactile fluidity defaults. */
        val Default = DynamicFluidityConfig()

        /** Subtle, minimal tactile deformation. */
        val Subtle = DynamicFluidityConfig(
            pressScale = 0.95f,
            pullFactor = 0.10f,
            elasticity = 0.30f
        )

        /** Highly expressive fluid jelly kinematics. */
        val HighBouncy = DynamicFluidityConfig(
            pressScale = 0.85f,
            pullFactor = 0.25f,
            elasticity = 0.75f
        )

        /** Completely disabled bubble fluidity. */
        val Disabled = DynamicFluidityConfig(enabled = false)
    }
}

/**
 * Fine-grained physics configuration for the expanded state of dynamic islands and pills.
 *
 * Allows selectively enabling or exempting expanded surfaces (such as squircle cards / profile expanded)
 * from any of the tactile bubble properties ([pressScale], [pullFactor], [elasticity]).
 *
 * @param enabled Master toggle: if false, the expanded surface is completely exempt from bubble fluidity.
 * @param pressScale Press squish/swell scale when expanded (null = inherit collapsed [pressScale], 1.0f = exempt/neutral).
 * @param pullFactor Drag pull tracking factor when expanded (null = inherit collapsed [pullFactor], 0.0f = exempt/stationary).
 * @param elasticity Jelly stretch elasticity when expanded (null = inherit collapsed [elasticity], 0.0f = exempt/rigid).
 * @param scalePressSpeed Press parallax response speed when expanded (null = inherit collapsed [scalePressSpeed]).
 * @param scalePressDamping Press parallax smoothness / damping when expanded (null = inherit collapsed [scalePressDamping]).
 * @param scaleSettleSpeed Release-to-settle spring speed when expanded (null = inherit collapsed [scaleSettleSpeed]).
 */
data class ExpandedBubbleFluidityConfig(
    val enabled: Boolean = true,
    val pressScale: Float? = null,
    val pullFactor: Float? = null,
    val elasticity: Float? = null,
    val scalePressSpeed: Float? = null,
    val scalePressDamping: Float? = null,
    val scaleSettleSpeed: Float? = null,
) {
    companion object {
        /** Completely exempt from all expanded bubble fluidity (natural default for rectangular cards / squircles). */
        val Exempt = ExpandedBubbleFluidityConfig(
            enabled = false,
            pressScale = 1.0f,
            pullFactor = 0.0f,
            elasticity = 0.0f,
            scalePressSpeed = null,
            scalePressDamping = null,
            scaleSettleSpeed = null,
        )

        /** Inherits all collapsed bubble fluidity properties (natural default for stadium pills). */
        val Inherit = ExpandedBubbleFluidityConfig(
            enabled = true,
            pressScale = null,
            pullFactor = null,
            elasticity = null,
            scalePressSpeed = null,
            scalePressDamping = null,
            scaleSettleSpeed = null,
        )
    }
}

/**
 * Grouped shadow configuration containing both unexpanded (collapsed) and expanded shadow variants.
 *
 * @param unexpanded Grounded resting shadow configuration.
 * @param expanded Elevated floating shadow configuration.
 */
data class DynamicShadowVariants(
    val unexpanded: LiquidGlassShadowProperties = LiquidGlassShadowProperties.Unexpanded,
    val expanded: LiquidGlassShadowProperties = LiquidGlassShadowProperties.Expanded,
) {
    companion object {
        /** Default shadow pair: 4dp drop resting to 28dp atmospheric elevated floating shadow. */
        val Default = DynamicShadowVariants()

        /** Signature search island shadow pair with deep 33dp drop at 36dp offset. */
        val SearchIsland = DynamicShadowVariants(
            unexpanded = LiquidGlassShadowProperties.Unexpanded,
            expanded = LiquidGlassShadowProperties.ExpandedSearch,
        )

        /** Zero shadow pair for flat or embedded glass surfaces. */
        val None = DynamicShadowVariants(
            unexpanded = LiquidGlassShadowProperties.None,
            expanded = LiquidGlassShadowProperties.None,
        )
    }
}

/**
 * Surface color and tint configuration for dynamic morphing buttons across resting and expanded states.
 *
 * @param restingColor Background tint color when collapsed at rest.
 * @param restingAlpha Background tint opacity when collapsed at rest (0f..1f).
 * @param expandedColor Background tint color when expanded.
 * @param expandedAlpha Background tint opacity when expanded (0f..1f).
 */
data class DynamicColorVariants(
    val restingColor: Color = Color(0xFF121214),
    val restingAlpha: Float = 0.85f,
    val expandedColor: Color = Color(0xFF1E1E24),
    val expandedAlpha: Float = 0.88f,
) {
    companion object {
        /** Default signature dark liquid glass colors. */
        val Default = DynamicColorVariants()

        /** Midnight black solid optical glass. */
        val Midnight = DynamicColorVariants(
            restingColor = Color(0xFF000000),
            restingAlpha = 0.95f,
            expandedColor = Color(0xFF0A0A0E),
            expandedAlpha = 0.92f,
        )

        /** Deep slate aesthetic glass. */
        val Slate = DynamicColorVariants(
            restingColor = Color(0xFF0F172A),
            restingAlpha = 0.85f,
            expandedColor = Color(0xFF1E293B),
            expandedAlpha = 0.88f,
        )

        /** Pure frosted white / light glass. */
        val FrostedWhite = DynamicColorVariants(
            restingColor = Color(0xFFFFFFFF),
            restingAlpha = 0.70f,
            expandedColor = Color(0xFFF8FAFC),
            expandedAlpha = 0.85f,
        )

        /** Electric Indigo / Brand accent glass. */
        val Indigo = DynamicColorVariants(
            restingColor = Color(0xFF3730A3),
            restingAlpha = 0.85f,
            expandedColor = Color(0xFF312E81),
            expandedAlpha = 0.90f,
        )

        /** Emerald / Neon glass. */
        val Emerald = DynamicColorVariants(
            restingColor = Color(0xFF064E3B),
            restingAlpha = 0.85f,
            expandedColor = Color(0xFF065F46),
            expandedAlpha = 0.90f,
        )
    }
}

/**
 * Transient optical content blur configuration applied during morphing state transitions.
 *
 * Simulates optical motion blur / depth-of-field dissipation on the button's internal child content
 * as the container springs outward or inward, preventing visual pops during structural morphs.
 *
 * @param enabled Master toggle for transient content blur.
 * @param maxBlur Peak blur radius reached during the expansion/collapse transient.
 * @param blurOnExpand Whether blur is applied when expanding outward.
 * @param blurOnCollapse Whether blur is applied when collapsing inward.
 * @param riseDurationMillis Duration in milliseconds for the blur to reach peak intensity.
 */
data class DynamicContentBlurConfig(
    val enabled: Boolean = true,
    val maxBlur: Dp = 8.dp,
    val blurOnExpand: Boolean = true,
    val blurOnCollapse: Boolean = true,
    val riseDurationMillis: Int = 75,
) {
    /** Standard rise animation spec for transient content blur. */
    val riseAnimationSpec: AnimationSpec<Float>
        get() = tween(durationMillis = riseDurationMillis, easing = FastOutSlowInEasing)

    companion object {
        /** CodeDeX tuned signature subtle content blur. */
        val Default = DynamicContentBlurConfig()

        /** Delicate subtle content blur for minimal distraction. */
        val Subtle = DynamicContentBlurConfig(
            maxBlur = 5.dp,
            riseDurationMillis = 60
        )

        /** Pronounced cinematic motion blur for dramatic transitions. */
        val Cinematic = DynamicContentBlurConfig(
            maxBlur = 14.dp,
            riseDurationMillis = 90
        )

        /** Completely disabled content blur (tack sharp at all times). */
        val Disabled = DynamicContentBlurConfig(enabled = false, maxBlur = 0.dp)
    }
}

/**
 * Anchor alignment for dynamic expansion specifying the directional origin of the motion.
 */
enum class ExpansionAnchor {
    /** Left/leading anchored: expands toward the right, nudging rightward (+X) during anticipation. */
    Start,

    /** Right/trailing anchored: expands toward the left, nudging leftward (-X) during anticipation. */
    End,

    /** Center anchored: expands symmetrically with zero horizontal displacement during anticipation. */
    Center
}

/**
 * Organic anticipation and parallax overshoot scaling configuration for buttons and surfaces as they expand.
 *
 * Encapsulates:
 * 1. Anticipation nudge: subtle directional shift ([nudgeDistance]) towards or into the expansion direction.
 * 2. Anticipation squish: subtle dual-axis compression ([squishScale]) using Poisson parallax (leading X, lagging Y).
 * 3. Expansion rebound: dynamic bounds growth accompanied by an elastic scale spring that shoots past 1.0f
 *    to peak at ~1.035x before settling elastically.
 *
 * @param enabled Master toggle for expanding anticipation physics.
 * @param nudgeDistance Distance in dp to subtly nudge during anticipation (default: +32.dp).
 * @param squishScale Scale multiplier during anticipation (default: 1.25f swell charge-up).
 * @param anticipationDurationMillis Duration in milliseconds for the anticipation windup before expanding (default: 70ms).
 * @param overshootVelocityMultiplier Initial velocity multiplier for the rebound scale spring (default: -20f damped undershoot).
 * @param nudgeSpeed Stiffness speed for anticipation nudge displacement (default: 350f).
 */
data class DynamicAnticipationConfig(
    val enabled: Boolean = true,
    val nudgeDistance: Dp = 32.dp,
    val squishScale: Float = 1.25f,
    val anticipationDurationMillis: Int = 70,
    val overshootVelocityMultiplier: Float = -20f,
    val nudgeSpeed: Float = 350f,
) {
    companion object {
        /** CodeDeX tuned authentic anticipation, swell, and undershoot defaults. */
        val Default = DynamicAnticipationConfig()

        /** Delicate anticipation with minimal nudge and soft swell. */
        val Subtle = DynamicAnticipationConfig(
            nudgeDistance = 16.dp,
            squishScale = 1.10f,
            anticipationDurationMillis = 60,
            overshootVelocityMultiplier = -12f,
            nudgeSpeed = 300f,
        )

        /** Expressive energetic anticipation with pronounced lunge. */
        val Expressive = DynamicAnticipationConfig(
            nudgeDistance = 35.dp,
            squishScale = 1.30f,
            anticipationDurationMillis = 90,
            overshootVelocityMultiplier = -25f,
            nudgeSpeed = 450f,
        )

        /** Completely disabled anticipation (no nudge or squish on expansion). */
        val Disabled = DynamicAnticipationConfig(enabled = false)
    }
}

/**
 * Unified composite configuration for [DynamicPillButton].
 */
data class DynamicPillConfig(
    val dimensions: DynamicDimensions = DynamicDimensions.PillDefault,
    val motion: DynamicMotionConfig = DynamicMotionConfig.Default,
    val fluidity: DynamicFluidityConfig = DynamicFluidityConfig.Default,
    val expandedFluidity: ExpandedBubbleFluidityConfig = ExpandedBubbleFluidityConfig.Inherit,
    val shadows: DynamicShadowVariants = DynamicShadowVariants.Default,
    val colors: DynamicColorVariants = DynamicColorVariants.Default,
    val contentBlur: DynamicContentBlurConfig = DynamicContentBlurConfig.Default,
    val anticipation: DynamicAnticipationConfig = DynamicAnticipationConfig.Default,
    val collapsedGlassConfig: LiquidGlassConfig? = null,
    val expandedGlassConfig: LiquidGlassConfig? = null,
) {
    companion object {
        val Default = DynamicPillConfig()
    }
}

