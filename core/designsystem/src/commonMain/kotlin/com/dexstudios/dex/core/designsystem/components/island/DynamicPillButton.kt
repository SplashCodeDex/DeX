package com.dexstudios.dex.core.designsystem.components.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare

/**
 * Standard configuration defaults for [DynamicPillButton].
 */
object DynamicPillDefaults {

    /** Default content transition inside the pill. */
    val ContentTransition: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = {
        val enterSpec = spring<Float>(dampingRatio = 0.70f, stiffness = 500f)
        val exitSpec = spring<Float>(dampingRatio = 0.70f, stiffness = 500f)
        (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = enterSpec))
            .togetherWith(fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.88f, animationSpec = exitSpec))
    }

    /** Grouped default configurations. */
    val DimensionsDefault: DynamicDimensions = DynamicDimensions.PillDefault
    val MotionDefault: DynamicMotionConfig = DynamicMotionConfig.Default
    val FluidityDefault: DynamicFluidityConfig = DynamicFluidityConfig.Default
    val ExpandedFluidityDefault: ExpandedBubbleFluidityConfig = ExpandedBubbleFluidityConfig.Inherit
    val ShadowsDefault: DynamicShadowVariants = DynamicShadowVariants.Default
    val ColorsDefault: DynamicColorVariants = DynamicColorVariants.Default
    val ContentBlurDefault: DynamicContentBlurConfig = DynamicContentBlurConfig.Default
    val AnticipationDefault: DynamicAnticipationConfig = DynamicAnticipationConfig.Default

    /** Backward-compatibility shadow aliases. */
    val UnexpandedShadowDefault: LiquidGlassShadowProperties = ShadowsDefault.unexpanded
    val ExpandedShadowDefault: LiquidGlassShadowProperties = ShadowsDefault.expanded
}

/**
 * Centralized, multiplatform Dynamic Pill Button with organic overshoot spring kinematics.
 *
 * Unlike rectangular or squircle islands, this button maintains a strict **stadium pill geometry**
 * ([CircleShape]) across all expansion states. When expanding horizontally or vertically, both caps
 * remain continuous semicircles at every frame.
 *
 * Encapsulates:
 * 1. Compact resting state (circle / pill) with tactile squish/swell physics ([bubbleFluidity]).
 * 2. Elongated stadium state with perfectly rounded semicircular caps (never rectangular).
 * 3. Direction-aware dual damping: distinct damping ratios for expanding vs. collapsing.
 * 4. Fluid, synchronous bounds morphing (width and height) with overshoot springs.
 * 5. Multi-tier dynamic shadow elevation morphing.
 * 6. Native frosted surface fill and directional specular rim ([shinyGlare]).
 * 7. Hardware-accelerated anticipation squish and directional nudge ([expandingAnticipation]).
 * 8. Grouped configuration settings for modular reuse ([DynamicDimensions], [DynamicMotionConfig],
 *    [DynamicFluidityConfig], [ExpandedBubbleFluidityConfig], [DynamicShadowVariants], [DynamicColorVariants],
 *    [DynamicContentBlurConfig]).
 * 9. Transient optical motion blur on slot contents during rapid morphing ([transientContentBlur]).
 * 10. 100% decoupled from project domain models via idiomatic Compose slots.
 */
@Composable
fun DynamicPillButton(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dimensions: DynamicDimensions = DynamicPillDefaults.DimensionsDefault,
    motion: DynamicMotionConfig = DynamicPillDefaults.MotionDefault,
    fluidity: DynamicFluidityConfig = DynamicPillDefaults.FluidityDefault,
    expandedFluidityConfig: ExpandedBubbleFluidityConfig = DynamicPillDefaults.ExpandedFluidityDefault,
    shadows: DynamicShadowVariants = DynamicPillDefaults.ShadowsDefault,
    colors: DynamicColorVariants = DynamicPillDefaults.ColorsDefault,
    contentBlur: DynamicContentBlurConfig = DynamicPillDefaults.ContentBlurDefault,
    anticipation: DynamicAnticipationConfig = DynamicPillDefaults.AnticipationDefault,
    expansionAnchor: ExpansionAnchor = ExpansionAnchor.Center,
    dismissOnOutsideTap: Boolean = true,
    withGlare: Boolean = true,
    contentTransitionSpec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = DynamicPillDefaults.ContentTransition,
    // Optional individual property overrides for granular inline tweaks
    collapsedWidth: Dp = dimensions.collapsedWidth,
    collapsedHeight: Dp = dimensions.collapsedHeight,
    expandedWidth: Dp = dimensions.expandedWidth,
    expandedHeight: Dp = dimensions.expandedHeight,
    expandDampingRatio: Float = motion.expandDampingRatio,
    collapseDampingRatio: Float = motion.collapseDampingRatio,
    stiffness: Float = motion.stiffness,
    morphSpringSpec: AnimationSpec<Dp>? = motion.springSpec,
    unexpandedShadow: LiquidGlassShadowProperties = shadows.unexpanded,
    expandedShadow: LiquidGlassShadowProperties = shadows.expanded,
    restingColor: Color = colors.restingColor,
    restingAlpha: Float = colors.restingAlpha,
    expandedColor: Color = colors.expandedColor,
    expandedAlpha: Float = colors.expandedAlpha,
    enableBubbleFluidity: Boolean = fluidity.enabled,
    pressScale: Float = fluidity.pressScale,
    pullFactor: Float = fluidity.pullFactor,
    elasticity: Float = fluidity.elasticity,
    scalePressSpeed: Float = fluidity.scalePressSpeed,
    scalePressDamping: Float = fluidity.scalePressDamping,
    scaleSettleSpeed: Float = fluidity.scaleSettleSpeed,
    enableExpandedBubbleFluidity: Boolean = expandedFluidityConfig.enabled,
    expandedPressScale: Float = expandedFluidityConfig.pressScale ?: pressScale,
    expandedPullFactor: Float = expandedFluidityConfig.pullFactor ?: pullFactor,
    expandedElasticity: Float = expandedFluidityConfig.elasticity ?: elasticity,
    expandedScalePressSpeed: Float = expandedFluidityConfig.scalePressSpeed ?: scalePressSpeed,
    expandedScalePressDamping: Float = expandedFluidityConfig.scalePressDamping ?: scalePressDamping,
    expandedScaleSettleSpeed: Float = expandedFluidityConfig.scaleSettleSpeed ?: scaleSettleSpeed,
    enableContentBlur: Boolean = contentBlur.enabled,
    maxContentBlur: Dp = contentBlur.maxBlur,
    blurRiseDurationMillis: Int = contentBlur.riseDurationMillis,
    blurOnExpand: Boolean = contentBlur.blurOnExpand,
    blurOnCollapse: Boolean = contentBlur.blurOnCollapse,
    collapsedContent: @Composable () -> Unit,
    expandedContent: @Composable (collapse: () -> Unit) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    // Active motion & fluidity configurations
    val activeMotionConfig = remember(expandDampingRatio, collapseDampingRatio, stiffness) {
        DynamicMotionConfig(
            expandDampingRatio = expandDampingRatio,
            collapseDampingRatio = collapseDampingRatio,
            stiffness = stiffness,
        )
    }
    val activeFluidityConfig = remember(pressScale, pullFactor, elasticity, scalePressSpeed, scalePressDamping, scaleSettleSpeed) {
        DynamicFluidityConfig(
            pressScale = pressScale,
            pullFactor = pullFactor,
            elasticity = elasticity,
            scalePressSpeed = scalePressSpeed,
            scalePressDamping = scalePressDamping,
            scaleSettleSpeed = scaleSettleSpeed,
        )
    }

    // Anticipation directional nudge & dual-axis Poisson parallax overshoot scaling
    val anticipationState = rememberExpandingAnticipationPhysics(
        isExpanded = isExpanded,
        anchor = expansionAnchor,
        config = anticipation,
        motion = activeMotionConfig,
        fluidity = activeFluidityConfig,
    )

    // Direction-aware spring spec: expandDampingRatio when opening, collapseDampingRatio when closing
    val activeSpringSpec = morphSpringSpec ?: remember(isExpanded, expandDampingRatio, collapseDampingRatio, stiffness) {
        spring(
            dampingRatio = if (isExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness,
        )
    }

    // Dynamic morphing dimensions with overshoot springs
    // During anticipation windup (~75ms), bounds remain at collapsed resting size while the button
    // squishes and nudges directionally. When anticipation completes, bounds expand outward to full target size.
    val shouldExpandBounds = isExpanded && (!anticipation.enabled || anticipationState.canExpandBounds)

    val targetWidth = if (shouldExpandBounds) expandedWidth else collapsedWidth
    val currentWidth by animateDpAsState(
        targetValue = if (targetWidth.isSpecified) targetWidth else collapsedWidth,
        animationSpec = activeSpringSpec,
        label = "pillMorphWidth",
    )

    val targetHeight = if (shouldExpandBounds) expandedHeight else collapsedHeight
    val currentHeight by animateDpAsState(
        targetValue = if (targetHeight.isSpecified) targetHeight else collapsedHeight,
        animationSpec = activeSpringSpec,
        label = "pillMorphHeight",
    )

    // Dynamic shadow properties: morph smoothly between unexpanded and expanded states
    val currentShadowRadius by animateDpAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.radius else unexpandedShadow.radius,
        animationSpec = activeSpringSpec,
        label = "pillShadowRadius",
    )
    val currentShadowAlpha by animateFloatAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.alpha else unexpandedShadow.alpha,
        animationSpec = spring(
            dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness,
        ),
        label = "pillShadowAlpha",
    )
    val currentShadowColor by animateColorAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.color else unexpandedShadow.color,
        animationSpec = spring(
            dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness,
        ),
        label = "pillShadowColor",
    )

    // Dynamic surface color & tint: morph smoothly between resting and expanded states
    val currentSurfaceColor by animateColorAsState(
        targetValue = if (shouldExpandBounds) expandedColor else restingColor,
        animationSpec = spring(
            dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness,
        ),
        label = "pillSurfaceColor",
    )
    val currentSurfaceAlpha by animateFloatAsState(
        targetValue = if (shouldExpandBounds) expandedAlpha else restingAlpha,
        animationSpec = spring(
            dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness,
        ),
        label = "pillSurfaceAlpha",
    )

    // Resolved centralized content blur configuration
    val activeContentBlur = remember(
        enableContentBlur,
        maxContentBlur,
        blurRiseDurationMillis,
        blurOnExpand,
        blurOnCollapse,
    ) {
        DynamicContentBlurConfig(
            enabled = enableContentBlur,
            maxBlur = maxContentBlur,
            blurOnExpand = blurOnExpand,
            blurOnCollapse = blurOnCollapse,
            riseDurationMillis = blurRiseDurationMillis,
        )
    }

    // Resolved active fluidity configuration (collapsed vs. expanded state)
    val activeFluidityEnabled = if (shouldExpandBounds) enableExpandedBubbleFluidity else enableBubbleFluidity
    val activePressScale = if (shouldExpandBounds) expandedPressScale else pressScale
    val activePullFactor = if (shouldExpandBounds) expandedPullFactor else pullFactor
    val activeElasticity = if (shouldExpandBounds) expandedElasticity else elasticity
    val activeScalePressSpeed = if (shouldExpandBounds) expandedScalePressSpeed else scalePressSpeed
    val activeScalePressDamping = if (shouldExpandBounds) expandedScalePressDamping else scalePressDamping
    val activeScaleSettleSpeed = if (shouldExpandBounds) expandedScaleSettleSpeed else scaleSettleSpeed

    val activeFluidity = remember(
        activeFluidityEnabled,
        activePressScale,
        activePullFactor,
        activeElasticity,
        activeScalePressSpeed,
        activeScalePressDamping,
        activeScaleSettleSpeed,
    ) {
        DynamicFluidityConfig(
            enabled = activeFluidityEnabled,
            pressScale = activePressScale,
            pullFactor = activePullFactor,
            elasticity = activeElasticity,
            scalePressSpeed = activeScalePressSpeed,
            scalePressDamping = activeScalePressDamping,
            scaleSettleSpeed = activeScaleSettleSpeed,
        )
    }

    val interactionSource = remember { MutableInteractionSource() }

    val sizeModifier = if (targetWidth.isSpecified) {
        Modifier.size(currentWidth, currentHeight)
    } else {
        Modifier.fillMaxWidth().height(currentHeight)
    }

    val clickModifier = if (!isExpanded && enabled) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onExpandedChange(true)
            },
        )
    } else {
        Modifier
    }

    val hoverModifier = Modifier.pointerHoverIcon(
        if (enabled && !isExpanded) PointerIcon.Hand else PointerIcon.Default,
    )

    // Main morphing stadium container
    Box(
        modifier = modifier
            .then(sizeModifier)
            .expandingAnticipation(anticipationState)
            .bubbleFluidity(config = activeFluidity)
            .shadow(
                elevation = currentShadowRadius,
                shape = CircleShape,
                spotColor = currentShadowColor.copy(alpha = currentShadowAlpha),
                ambientColor = currentShadowColor.copy(alpha = currentShadowAlpha * 0.5f),
            )
            .clip(CircleShape)
            .background(currentSurfaceColor.copy(alpha = currentSurfaceAlpha))
            .then(if (withGlare) Modifier.shinyGlare(shape = CircleShape) else Modifier)
            .then(hoverModifier)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = shouldExpandBounds,
                transitionSpec = contentTransitionSpec,
                modifier = Modifier.transientContentBlur(
                    trigger = shouldExpandBounds,
                    config = activeContentBlur,
                    motion = activeMotionConfig,
                ),
                label = "pillSlotContent",
            ) { expandedState ->
                if (expandedState) {
                    expandedContent {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpandedChange(false)
                    }
                } else {
                    collapsedContent()
                }
            }
        }
    }
}

/**
 * Overload of [DynamicPillButton] accepting a unified [DynamicPillConfig].
 */
@Composable
fun DynamicPillButton(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    config: DynamicPillConfig,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expansionAnchor: ExpansionAnchor = ExpansionAnchor.Center,
    dismissOnOutsideTap: Boolean = true,
    withGlare: Boolean = true,
    contentTransitionSpec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = DynamicPillDefaults.ContentTransition,
    collapsedContent: @Composable () -> Unit,
    expandedContent: @Composable (collapse: () -> Unit) -> Unit,
) = DynamicPillButton(
    isExpanded = isExpanded,
    onExpandedChange = onExpandedChange,
    modifier = modifier,
    enabled = enabled,
    dimensions = config.dimensions,
    motion = config.motion,
    fluidity = config.fluidity,
    expandedFluidityConfig = config.expandedFluidity,
    shadows = config.shadows,
    colors = config.colors,
    contentBlur = config.contentBlur,
    anticipation = config.anticipation,
    expansionAnchor = expansionAnchor,
    dismissOnOutsideTap = dismissOnOutsideTap,
    withGlare = withGlare,
    contentTransitionSpec = contentTransitionSpec,
    collapsedContent = collapsedContent,
    expandedContent = expandedContent,
)
