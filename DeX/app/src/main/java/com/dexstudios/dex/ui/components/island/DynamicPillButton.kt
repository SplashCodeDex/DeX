package com.dexstudios.dex.ui.components.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassIconButton
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassShadowProperties
import com.kyant.backdrop.Backdrop

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

    /** Grouped default configurations */
    val DimensionsDefault: DynamicDimensions = DynamicDimensions.PillDefault
    val MotionDefault: DynamicMotionConfig = DynamicMotionConfig.Default
    val FluidityDefault: DynamicFluidityConfig = DynamicFluidityConfig.Default
    val ExpandedFluidityDefault: ExpandedBubbleFluidityConfig = ExpandedBubbleFluidityConfig.Inherit
    val ShadowsDefault: DynamicShadowVariants = DynamicShadowVariants.Default
    val ColorsDefault: DynamicColorVariants = DynamicColorVariants.Default
    val ContentBlurDefault: DynamicContentBlurConfig = DynamicContentBlurConfig.Default
    val AnticipationDefault: DynamicAnticipationConfig = DynamicAnticipationConfig.Default

    /** Backward-compatibility shadow aliases */
    val UnexpandedShadowDefault: LiquidGlassShadowProperties = ShadowsDefault.unexpanded
    val ExpandedShadowDefault: LiquidGlassShadowProperties = ShadowsDefault.expanded
}

/**
 * Centralized, standard Dynamic Pill Button with organic overshoot spring kinematics.
 *
 * Unlike rectangular or squircle islands, this button maintains a strict **stadium pill geometry**
 * ([CircleShape]) across all expansion states. When expanding horizontally or vertically, both caps
 * remain continuous semicircles at every frame.
 *
 * Encapsulates:
 * 1. Compact resting state (circle / pill) with tactile squish/swell physics.
 * 2. Elongated pill state with perfectly rounded semicircular caps (never rectangular).
 * 3. Direction-aware dual damping: distinct damping ratios for expanding vs. collapsing.
 * 4. Fluid, synchronous bounds morphing (width and height) with overshoot springs.
 * 5. Outside-tap dismissal scrim.
 * 6. Grouped configuration settings for modular reuse ([DynamicDimensions], [DynamicMotionConfig],
 *    [DynamicFluidityConfig], [ExpandedBubbleFluidityConfig], [DynamicShadowVariants], [DynamicColorVariants],
 *    [DynamicContentBlurConfig]).
 * 7. Transient optical motion blur on slot contents during rapid morphing.
 * 8. 100% decoupled from project domain models via idiomatic Compose slots.
 */
/**
 * Centralized, standard Dynamic Pill Button with organic overshoot spring kinematics.
 *
 * Unlike rectangular or squircle islands, this button maintains a strict **stadium pill geometry**
 * ([CircleShape]) across all expansion states. When expanding horizontally or vertically, both caps
 * remain continuous semicircles at every frame.
 *
 * Supports Apple Dynamic Island style 3-tier spatial regulation via [DynamicPillStage]:
 * 1. [DynamicPillStage.Collapsed]: Compact resting state (0.dp hidden, or circle/pill).
 * 2. [DynamicPillStage.Compact]: Regulated auxiliary state (e.g. 56.dp circular capsule showing icon + mini count).
 * 3. [DynamicPillStage.Expanded]: Full elongated stadium pill with perfectly rounded semicircular caps.
 */
@Composable
fun DynamicPillButton(
    stage: DynamicPillStage,
    onStageChange: (DynamicPillStage) -> Unit,
    modifier: Modifier = Modifier,
    dimensions: DynamicDimensions = DynamicPillDefaults.DimensionsDefault,
    motion: DynamicMotionConfig = DynamicPillDefaults.MotionDefault,
    fluidity: DynamicFluidityConfig = DynamicPillDefaults.FluidityDefault,
    expandedFluidityConfig: ExpandedBubbleFluidityConfig = DynamicPillDefaults.ExpandedFluidityDefault,
    shadows: DynamicShadowVariants = DynamicPillDefaults.ShadowsDefault,
    colors: DynamicColorVariants = DynamicPillDefaults.ColorsDefault,
    contentBlur: DynamicContentBlurConfig = DynamicPillDefaults.ContentBlurDefault,
    collapsedGlassConfig: LiquidGlassConfig = LiquidGlassPresets.ProfileIconButton.copy(shape = CircleShape),
    compactGlassConfig: LiquidGlassConfig = collapsedGlassConfig,
    expandedGlassConfig: LiquidGlassConfig = LiquidGlassPresets.ProfileIsland.copy(shape = CircleShape),
    fullIslandGlassConfig: LiquidGlassConfig = LiquidGlassPresets.ProfileIsland.copy(shape = CircleShape),
    backdrop: Backdrop? = null,
    dismissOnOutsideTap: Boolean = true,
    collapsedWidth: Dp = dimensions.collapsedWidth,
    collapsedHeight: Dp = dimensions.collapsedHeight,
    compactWidth: Dp = dimensions.compactWidth,
    compactHeight: Dp = dimensions.compactHeight,
    expandedWidth: Dp = dimensions.expandedWidth,
    expandedHeight: Dp = dimensions.expandedHeight,
    fullIslandWidth: Dp = dimensions.fullIslandWidth,
    fullIslandHeight: Dp = dimensions.fullIslandHeight,
    expandDampingRatio: Float = motion.expandDampingRatio,
    collapseDampingRatio: Float = motion.collapseDampingRatio,
    stiffness: Float = motion.stiffness,
    morphSpringSpec: AnimationSpec<Dp>? = motion.springSpec,
    unexpandedShadow: LiquidGlassShadowProperties = shadows.unexpanded,
    expandedShadow: LiquidGlassShadowProperties = shadows.expanded,
    restingColor: Color = if (collapsedGlassConfig.surfaceTint.isSpecified && colors == DynamicColorVariants.Default) collapsedGlassConfig.surfaceTint else colors.restingColor,
    restingAlpha: Float = if (collapsedGlassConfig.surfaceTintAlpha > 0f && colors == DynamicColorVariants.Default) collapsedGlassConfig.surfaceTintAlpha else colors.restingAlpha,
    expandedColor: Color = if (expandedGlassConfig.surfaceTint.isSpecified && colors == DynamicColorVariants.Default) expandedGlassConfig.surfaceTint else colors.expandedColor,
    expandedAlpha: Float = if (expandedGlassConfig.surfaceTintAlpha > 0f && colors == DynamicColorVariants.Default) expandedGlassConfig.surfaceTintAlpha else colors.expandedAlpha,
    fullIslandColor: Color = if (fullIslandGlassConfig.surfaceTint.isSpecified && colors == DynamicColorVariants.Default) fullIslandGlassConfig.surfaceTint else expandedColor,
    fullIslandAlpha: Float = if (fullIslandGlassConfig.surfaceTintAlpha > 0f && colors == DynamicColorVariants.Default) fullIslandGlassConfig.surfaceTintAlpha else expandedAlpha,
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
    expansionAnchor: ExpansionAnchor = ExpansionAnchor.Center,
    anticipation: DynamicAnticipationConfig = DynamicPillDefaults.AnticipationDefault,
    collapsedContent: @Composable () -> Unit,
    compactContent: (@Composable () -> Unit)? = null,
    expandedContent: @Composable (collapse: () -> Unit) -> Unit,
    fullIslandContent: (@Composable (collapse: () -> Unit) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val isAnyExpanded = stage != DynamicPillStage.Collapsed
    val isFullyExpanded = stage == DynamicPillStage.Expanded || stage == DynamicPillStage.FullIsland

    // Anticipation directional nudge & dual-axis Poisson parallax overshoot scaling
    val activeMotionConfig = remember(expandDampingRatio, collapseDampingRatio, stiffness) {
        DynamicMotionConfig(
            expandDampingRatio = expandDampingRatio,
            collapseDampingRatio = collapseDampingRatio,
            stiffness = stiffness
        )
    }
    val activeFluidityConfig = remember(pressScale, pullFactor, elasticity, scalePressSpeed, scalePressDamping, scaleSettleSpeed) {
        DynamicFluidityConfig(
            pressScale = pressScale,
            pullFactor = pullFactor,
            elasticity = elasticity,
            scalePressSpeed = scalePressSpeed,
            scalePressDamping = scalePressDamping,
            scaleSettleSpeed = scaleSettleSpeed
        )
    }
    val anticipationState = rememberExpandingAnticipationPhysics(
        isExpanded = isAnyExpanded,
        triggerKey = stage,
        anchor = expansionAnchor,
        config = anticipation,
        motion = activeMotionConfig,
        fluidity = activeFluidityConfig
    )

    // Direction-aware spring spec: expandDampingRatio when opening, collapseDampingRatio when closing
    val activeSpringSpec = morphSpringSpec ?: remember(isAnyExpanded, expandDampingRatio, collapseDampingRatio, stiffness) {
        spring(
            dampingRatio = if (isAnyExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        )
    }

    // 1. Dynamic morphing dimensions with overshoot springs across 3 tiers
    val shouldExpandBounds = !anticipation.enabled || anticipationState.canExpandBounds || collapsedWidth == 0.dp || stage == DynamicPillStage.Collapsed
    val targetWidth = when (stage) {
        DynamicPillStage.Collapsed -> collapsedWidth
        DynamicPillStage.Compact -> if (shouldExpandBounds) compactWidth else collapsedWidth
        DynamicPillStage.Expanded -> if (shouldExpandBounds) expandedWidth else collapsedWidth
        DynamicPillStage.FullIsland -> if (shouldExpandBounds) fullIslandWidth else collapsedWidth
    }
    val targetHeight = when (stage) {
        DynamicPillStage.Collapsed -> collapsedHeight
        DynamicPillStage.Compact -> if (shouldExpandBounds) compactHeight else collapsedHeight
        DynamicPillStage.Expanded -> if (shouldExpandBounds) expandedHeight else collapsedHeight
        DynamicPillStage.FullIsland -> if (shouldExpandBounds) fullIslandHeight else collapsedHeight
    }

    val currentWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = activeSpringSpec,
        label = "pillMorphWidth"
    )
    val currentHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = activeSpringSpec,
        label = "pillMorphHeight"
    )

    // Dynamic shadow properties: morph smoothly between unexpanded and expanded states
    val currentShadowRadius by animateDpAsState(
        targetValue = if (isFullyExpanded) expandedShadow.radius else unexpandedShadow.radius,
        animationSpec = activeSpringSpec,
        label = "pillShadowRadius"
    )
    val currentShadowAlpha by animateFloatAsState(
        targetValue = if (isFullyExpanded) expandedShadow.alpha else unexpandedShadow.alpha,
        animationSpec = spring(
            dampingRatio = if (isAnyExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillShadowAlpha"
    )
    val currentShadowColor by animateColorAsState(
        targetValue = if (isFullyExpanded) expandedShadow.color else unexpandedShadow.color,
        animationSpec = spring(
            dampingRatio = if (isAnyExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillShadowColor"
    )
    val currentShadowOffsetX by animateDpAsState(
        targetValue = if (isFullyExpanded) expandedShadow.offsetX else unexpandedShadow.offsetX,
        animationSpec = activeSpringSpec,
        label = "pillShadowOffsetX"
    )
    val currentShadowOffsetY by animateDpAsState(
        targetValue = if (isFullyExpanded) expandedShadow.offsetY else unexpandedShadow.offsetY,
        animationSpec = activeSpringSpec,
        label = "pillShadowOffsetY"
    )

    val currentInnerShadowRadius by animateDpAsState(
        targetValue = if (isFullyExpanded) expandedShadow.innerRadius else unexpandedShadow.innerRadius,
        animationSpec = activeSpringSpec,
        label = "pillInnerShadowRadius"
    )
    val currentInnerShadowAlpha by animateFloatAsState(
        targetValue = if (isFullyExpanded) expandedShadow.innerAlpha else unexpandedShadow.innerAlpha,
        animationSpec = spring(
            dampingRatio = if (isAnyExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillInnerShadowAlpha"
    )
    val currentInnerShadowColor by animateColorAsState(
        targetValue = if (isFullyExpanded) expandedShadow.innerColor else unexpandedShadow.innerColor,
        animationSpec = spring(
            dampingRatio = if (isAnyExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillInnerShadowColor"
    )
    val currentInnerShadowOffsetX by animateDpAsState(
        targetValue = if (isFullyExpanded) expandedShadow.innerOffsetX else unexpandedShadow.innerOffsetX,
        animationSpec = activeSpringSpec,
        label = "pillInnerShadowOffsetX"
    )
    val currentInnerShadowOffsetY by animateDpAsState(
        targetValue = if (isFullyExpanded) expandedShadow.innerOffsetY else unexpandedShadow.innerOffsetY,
        animationSpec = activeSpringSpec,
        label = "pillInnerShadowOffsetY"
    )

    // Dynamic surface color & tint: morph smoothly between resting and expanded states
    val currentSurfaceColor by animateColorAsState(
        targetValue = when (stage) {
            DynamicPillStage.FullIsland -> fullIslandColor
            DynamicPillStage.Expanded -> expandedColor
            DynamicPillStage.Compact, DynamicPillStage.Collapsed -> restingColor
        },
        animationSpec = spring(
            dampingRatio = if (isAnyExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillSurfaceColor"
    )
    val currentSurfaceAlpha by animateFloatAsState(
        targetValue = when (stage) {
            DynamicPillStage.FullIsland -> fullIslandAlpha
            DynamicPillStage.Expanded -> expandedAlpha
            DynamicPillStage.Compact, DynamicPillStage.Collapsed -> restingAlpha
        },
        animationSpec = spring(
            dampingRatio = if (isAnyExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillSurfaceAlpha"
    )

    val currentShadowProperties = remember(
        currentShadowRadius,
        currentShadowColor,
        currentShadowAlpha,
        currentShadowOffsetX,
        currentShadowOffsetY,
        currentInnerShadowRadius,
        currentInnerShadowColor,
        currentInnerShadowAlpha,
        currentInnerShadowOffsetX,
        currentInnerShadowOffsetY
    ) {
        LiquidGlassShadowProperties(
            radius = currentShadowRadius,
            color = currentShadowColor,
            alpha = currentShadowAlpha,
            offset = DpOffset(currentShadowOffsetX, currentShadowOffsetY),
            innerRadius = currentInnerShadowRadius,
            innerColor = currentInnerShadowColor,
            innerAlpha = currentInnerShadowAlpha,
            innerOffset = DpOffset(currentInnerShadowOffsetX, currentInnerShadowOffsetY),
        )
    }

    // Resolved centralized content blur and fluidity configurations
    val activeContentBlur = remember(
        enableContentBlur,
        maxContentBlur,
        blurRiseDurationMillis,
        blurOnExpand,
        blurOnCollapse
    ) {
        DynamicContentBlurConfig(
            enabled = enableContentBlur,
            maxBlur = maxContentBlur,
            blurOnExpand = blurOnExpand,
            blurOnCollapse = blurOnCollapse,
            riseDurationMillis = blurRiseDurationMillis
        )
    }

    val activeFluidityEnabled = if (isFullyExpanded) enableExpandedBubbleFluidity else enableBubbleFluidity
    val activePressScale = if (isFullyExpanded) expandedPressScale else pressScale
    val activePullFactor = if (isFullyExpanded) expandedPullFactor else pullFactor
    val activeElasticity = if (isFullyExpanded) expandedElasticity else elasticity
    val activeScalePressSpeed = if (isFullyExpanded) expandedScalePressSpeed else scalePressSpeed
    val activeScalePressDamping = if (isFullyExpanded) expandedScalePressDamping else scalePressDamping
    val activeScaleSettleSpeed = if (isFullyExpanded) expandedScaleSettleSpeed else scaleSettleSpeed

    val activeFluidity = remember(
        activeFluidityEnabled,
        activePressScale,
        activePullFactor,
        activeElasticity,
        activeScalePressSpeed,
        activeScalePressDamping,
        activeScaleSettleSpeed
    ) {
        DynamicFluidityConfig(
            enabled = activeFluidityEnabled,
            pressScale = activePressScale,
            pullFactor = activePullFactor,
            elasticity = activeElasticity,
            scalePressSpeed = activeScalePressSpeed,
            scalePressDamping = activeScalePressDamping,
            scaleSettleSpeed = activeScaleSettleSpeed
        )
    }

    // 2. Strict pill/capsule shape enforcement (semicircular caps guaranteed)
    val pillShape = CircleShape
    val resolvedWidth = currentWidth.coerceAtLeast(0.dp)
    val resolvedHeight = currentHeight.coerceAtLeast(0.dp)

    if (resolvedWidth >= 1.dp && resolvedHeight >= 1.dp) {
        // 3. Main morphing pill container
        Box(
            modifier = modifier
                .size(resolvedWidth, resolvedHeight)
                .expandingAnticipation(anticipationState),
            contentAlignment = Alignment.Center
        ) {
            val activeConfig = remember(stage, collapsedGlassConfig, compactGlassConfig, expandedGlassConfig, fullIslandGlassConfig, currentSurfaceColor, currentSurfaceAlpha, currentShadowProperties) {
                val base = when (stage) {
                    DynamicPillStage.FullIsland -> fullIslandGlassConfig
                    DynamicPillStage.Expanded -> expandedGlassConfig
                    DynamicPillStage.Compact -> compactGlassConfig
                    DynamicPillStage.Collapsed -> collapsedGlassConfig
                }
                base.withShadowProperties(currentShadowProperties).copy(
                    shape = pillShape,
                    surfaceTint = currentSurfaceColor,
                    surfaceTintAlpha = currentSurfaceAlpha
                )
            }

            LiquidGlassIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onStageChange(DynamicPillStage.Expanded)
                },
                enabled = !isFullyExpanded,
                width = resolvedWidth,
                height = resolvedHeight,
                backdrop = backdrop,
                config = activeConfig,
                fluidity = activeFluidity
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(pillShape),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = stage,
                        transitionSpec = {
                            fadeIn(spring(stiffness = stiffness, dampingRatio = 0.55f)) togetherWith
                                fadeOut(spring(stiffness = stiffness, dampingRatio = 0.55f))
                        },
                        modifier = Modifier.transientContentBlur(
                            trigger = stage.isVisible,
                            config = activeContentBlur,
                            motion = activeMotionConfig
                        ),
                        label = "pillSlotContent"
                    ) { currentSlotStage ->
                        when (currentSlotStage) {
                            DynamicPillStage.Collapsed -> collapsedContent()
                            DynamicPillStage.Compact -> {
                                if (compactContent != null) compactContent()
                                else collapsedContent()
                            }
                            DynamicPillStage.Expanded -> {
                                expandedContent {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onStageChange(DynamicPillStage.Collapsed)
                                }
                            }
                            DynamicPillStage.FullIsland -> {
                                val islandSlot = fullIslandContent ?: expandedContent
                                islandSlot {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onStageChange(DynamicPillStage.Collapsed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standard 2-state overload of [DynamicPillButton] maintaining 100% backward compatibility
 * for binary expanded/collapsed components.
 */
@Composable
fun DynamicPillButton(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dimensions: DynamicDimensions = DynamicPillDefaults.DimensionsDefault,
    motion: DynamicMotionConfig = DynamicPillDefaults.MotionDefault,
    fluidity: DynamicFluidityConfig = DynamicPillDefaults.FluidityDefault,
    expandedFluidityConfig: ExpandedBubbleFluidityConfig = DynamicPillDefaults.ExpandedFluidityDefault,
    shadows: DynamicShadowVariants = DynamicPillDefaults.ShadowsDefault,
    colors: DynamicColorVariants = DynamicPillDefaults.ColorsDefault,
    contentBlur: DynamicContentBlurConfig = DynamicPillDefaults.ContentBlurDefault,
    collapsedGlassConfig: LiquidGlassConfig = LiquidGlassPresets.ProfileIconButton.copy(shape = CircleShape),
    expandedGlassConfig: LiquidGlassConfig = LiquidGlassPresets.ProfileIsland.copy(shape = CircleShape),
    backdrop: Backdrop? = null,
    dismissOnOutsideTap: Boolean = true,
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
    restingColor: Color = if (collapsedGlassConfig.surfaceTint.isSpecified && colors == DynamicColorVariants.Default) collapsedGlassConfig.surfaceTint else colors.restingColor,
    restingAlpha: Float = if (collapsedGlassConfig.surfaceTintAlpha > 0f && colors == DynamicColorVariants.Default) collapsedGlassConfig.surfaceTintAlpha else colors.restingAlpha,
    expandedColor: Color = if (expandedGlassConfig.surfaceTint.isSpecified && colors == DynamicColorVariants.Default) expandedGlassConfig.surfaceTint else colors.expandedColor,
    expandedAlpha: Float = if (expandedGlassConfig.surfaceTintAlpha > 0f && colors == DynamicColorVariants.Default) expandedGlassConfig.surfaceTintAlpha else colors.expandedAlpha,
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
    expansionAnchor: ExpansionAnchor = ExpansionAnchor.Center,
    anticipation: DynamicAnticipationConfig = DynamicPillDefaults.AnticipationDefault,
    collapsedContent: @Composable () -> Unit,
    expandedContent: @Composable (collapse: () -> Unit) -> Unit,
) = DynamicPillButton(
    stage = if (isExpanded) DynamicPillStage.Expanded else DynamicPillStage.Collapsed,
    onStageChange = { onExpandedChange(it == DynamicPillStage.Expanded) },
    modifier = modifier,
    dimensions = dimensions,
    motion = motion,
    fluidity = fluidity,
    expandedFluidityConfig = expandedFluidityConfig,
    shadows = shadows,
    colors = colors,
    contentBlur = contentBlur,
    collapsedGlassConfig = collapsedGlassConfig,
    compactGlassConfig = collapsedGlassConfig,
    expandedGlassConfig = expandedGlassConfig,
    backdrop = backdrop,
    dismissOnOutsideTap = dismissOnOutsideTap,
    collapsedWidth = collapsedWidth,
    collapsedHeight = collapsedHeight,
    compactWidth = collapsedWidth,
    compactHeight = collapsedHeight,
    expandedWidth = expandedWidth,
    expandedHeight = expandedHeight,
    expandDampingRatio = expandDampingRatio,
    collapseDampingRatio = collapseDampingRatio,
    stiffness = stiffness,
    morphSpringSpec = morphSpringSpec,
    unexpandedShadow = unexpandedShadow,
    expandedShadow = expandedShadow,
    restingColor = restingColor,
    restingAlpha = restingAlpha,
    expandedColor = expandedColor,
    expandedAlpha = expandedAlpha,
    enableBubbleFluidity = enableBubbleFluidity,
    pressScale = pressScale,
    pullFactor = pullFactor,
    elasticity = elasticity,
    scalePressSpeed = scalePressSpeed,
    scalePressDamping = scalePressDamping,
    scaleSettleSpeed = scaleSettleSpeed,
    enableExpandedBubbleFluidity = enableExpandedBubbleFluidity,
    expandedPressScale = expandedPressScale,
    expandedPullFactor = expandedPullFactor,
    expandedElasticity = expandedElasticity,
    expandedScalePressSpeed = expandedScalePressSpeed,
    expandedScalePressDamping = expandedScalePressDamping,
    expandedScaleSettleSpeed = expandedScaleSettleSpeed,
    enableContentBlur = enableContentBlur,
    maxContentBlur = maxContentBlur,
    blurRiseDurationMillis = blurRiseDurationMillis,
    blurOnExpand = blurOnExpand,
    blurOnCollapse = blurOnCollapse,
    expansionAnchor = expansionAnchor,
    anticipation = anticipation,
    collapsedContent = collapsedContent,
    compactContent = null,
    expandedContent = expandedContent
)

/**
 * Overload of [DynamicPillButton] accepting a unified [DynamicPillConfig].
 */
@Composable
fun DynamicPillButton(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    config: DynamicPillConfig,
    modifier: Modifier = Modifier,
    expansionAnchor: ExpansionAnchor = ExpansionAnchor.Center,
    backdrop: Backdrop? = null,
    dismissOnOutsideTap: Boolean = true,
    contentTransitionSpec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = DynamicPillDefaults.ContentTransition,
    collapsedContent: @Composable () -> Unit,
    expandedContent: @Composable (collapse: () -> Unit) -> Unit,
) = DynamicPillButton(
    isExpanded = isExpanded,
    onExpandedChange = onExpandedChange,
    modifier = modifier,
    dimensions = config.dimensions,
    motion = config.motion,
    fluidity = config.fluidity,
    expandedFluidityConfig = config.expandedFluidity,
    shadows = config.shadows,
    colors = config.colors,
    contentBlur = config.contentBlur,
    expansionAnchor = expansionAnchor,
    anticipation = config.anticipation,
    collapsedGlassConfig = config.collapsedGlassConfig ?: LiquidGlassPresets.ProfileIconButton.copy(shape = CircleShape),
    expandedGlassConfig = config.expandedGlassConfig ?: LiquidGlassPresets.ProfileIsland.copy(shape = CircleShape),
    backdrop = backdrop,
    dismissOnOutsideTap = dismissOnOutsideTap,
    contentTransitionSpec = contentTransitionSpec,
    collapsedContent = collapsedContent,
    expandedContent = expandedContent,
)
