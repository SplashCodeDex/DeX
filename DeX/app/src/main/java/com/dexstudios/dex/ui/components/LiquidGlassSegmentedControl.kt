package com.dexstudios.dex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.island.DynamicFluidityConfig
import com.dexstudios.dex.ui.components.island.DynamicMotionConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassShadowProperties
import com.dexstudios.dex.ui.components.glass.LiquidGlassTokens
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.theme.DeXTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.shadow.InnerShadow
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest

/**
 * Data model for an individual tab in the segmented control.
 */
data class SegmentedControlItem(
    val title: String,
    val icon: ImageVector? = null,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)

/**
 * 1:1 High-Performance Liquid Glass Segmented Pill Control with Signature Bulging Physics.
 *
 * Implements an authentic optical and physics pipeline:
 * 1. Base Layer (Track board + items) captured into a local [Backdrop].
 * 2. Floating Liquid Glass Highlighter sampling the captured layer with real-time refraction,
 *    3D vertical and horizontal bulging (+18dp height bulge, +60dp width stretch, lens magnification),
 *    spring-loaded translation, dynamic peaking, gesture tracking, and tactile haptics.
 */
@Composable
fun LiquidGlassSegmentedControl(
    items: List<SegmentedControlItem>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    totalWidth: Dp = 300.dp,
    visibleHeight: Dp = 56.dp,
    samplingHeight: Dp = 160.dp,
    lensHeight: Dp = 190.dp,
    lensAmount: Dp = 100.dp,
    restRefraction: Float = 0.11f,
    expansionFraction: Float = 1f,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return

    val selectedIndex by rememberUpdatedState(items.indexOfFirst { it.isSelected }.coerceAtLeast(0))
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var dragX by remember { mutableStateOf<Float?>(null) }
    val isInteracting = pressedIndex != null || dragX != null

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    val horizontalPadding = 1.dp
    val availableWidth = totalWidth - (horizontalPadding * 2)
    val itemWidth = availableWidth / items.size

    val selectedCenterDp = horizontalPadding + (itemWidth * selectedIndex) + (itemWidth / 2f)
    val isPeaking = pressedIndex != null && pressedIndex != selectedIndex

    // Add a directional peak shift towards the pressed tab
    val peakShiftDp =
        if (isPeaking) {
            val pressedCenterDp =
                horizontalPadding + (itemWidth * (pressedIndex ?: selectedIndex)) + (itemWidth / 2f)
            val diff = pressedCenterDp - selectedCenterDp
            if (diff > 0.dp) 20.dp else -20.dp
        } else 0.dp

    // Stretch width to create a teardrop shape pointing towards the finger
    val peakStretchDp = if (isPeaking) 24.dp else 0.dp

    // --- Highlighter position: drag follows finger, otherwise follows selected tab (with peak offset) ---
    val targetCenterDp =
        if (dragX != null) {
            with(density) { dragX!!.toDp() }
        } else {
            selectedCenterDp + peakShiftDp
        }

    val centerX = remember { Animatable(targetCenterDp.value) }

    // --- Kinematic Springs: CodeDeX tuned signature overshoot springs & direction-aware dual damping ---
    val lensSlideSpring = remember { spring<Float>(dampingRatio = 0.5f, stiffness = 170f) }

    val isMoving = centerX.isRunning
    val isHighlighterActive = isInteracting || isMoving

    LaunchedEffect(targetCenterDp.value, dragX != null) {
        if (dragX != null) {
            centerX.snapTo(targetCenterDp.value)
        } else {
            centerX.animateTo(
                targetValue = targetCenterDp.value,
                animationSpec = lensSlideSpring,
            )
        }
    }

    val bulgeSpringDp = remember(isInteracting) {
        spring<Dp>(
            dampingRatio = if (isInteracting) 0.5f else 0.56f,
            stiffness = 170f
        )
    }
    val bulgeSpringFloat = remember(isInteracting) {
        spring<Float>(
            dampingRatio = if (isInteracting) 0.5f else 0.56f,
            stiffness = 170f
        )
    }
    val activeSpringDp = remember(isHighlighterActive) {
        spring<Dp>(
            dampingRatio = if (isHighlighterActive) 0.5f else 0.56f,
            stiffness = 170f
        )
    }
    val activeSpringFloat = remember(isHighlighterActive) {
        spring<Float>(
            dampingRatio = if (isHighlighterActive) 0.5f else 0.56f,
            stiffness = 170f
        )
    }

    // --- Highlighter dynamic sizing & signature bulge on interact ---
    // At rest: sits neatly aligned inside track slot (itemWidth - 6dp, visibleHeight - 8dp)
    // On interact: BULGES OUT vertically (+16dp over track) and expands horizontally
    val restWidth = (itemWidth - 6.dp).coerceAtLeast(40.dp)
    val interactWidth = itemWidth * 1.35f

    val highlighterWidth by
        animateDpAsState(
            targetValue = (if (isInteracting) interactWidth else restWidth) + peakStretchDp,
            animationSpec = bulgeSpringDp,
            label = "hlW",
        )
    val highlighterHeight by
        animateDpAsState(
            targetValue = if (isInteracting) visibleHeight + 16.dp else visibleHeight - 8.dp,
            animationSpec = bulgeSpringDp,
            label = "hlH",
        )

    // --- Dynamic lens & refraction warp on interact ---
    val animatedLensHeight by
        animateDpAsState(
            targetValue = if (isInteracting) lensHeight else 0.dp,
            animationSpec = bulgeSpringDp,
            label = "lensH",
        )
    val animatedLensAmount by
        animateDpAsState(
            targetValue = if (isInteracting) lensAmount else 0.dp,
            animationSpec = bulgeSpringDp,
            label = "lensA",
        )
    val animatedRefraction by
        animateFloatAsState(
            targetValue = if (isInteracting) restRefraction else 0.20f,
            animationSpec = bulgeSpringFloat,
            label = "refr",
        )

    // --- Multi-Tier Liquid Glass Shadows (CodeDeX Tuned Preference) ---
    val unexpandedShadow = LiquidGlassShadowProperties.Unexpanded
    val expandedShadow = LiquidGlassShadowProperties.Expanded

    val animatedShadowRadius by
        animateDpAsState(
            targetValue = if (isHighlighterActive) expandedShadow.radius else unexpandedShadow.radius,
            animationSpec = activeSpringDp,
            label = "shadowRadius",
        )
    val animatedShadowAlpha by
        animateFloatAsState(
            targetValue = if (isHighlighterActive) expandedShadow.alpha else unexpandedShadow.alpha,
            animationSpec = activeSpringFloat,
            label = "shadowAlpha",
        )
    val animatedShadowOffsetY by
        animateDpAsState(
            targetValue = if (isHighlighterActive) expandedShadow.offsetY else unexpandedShadow.offsetY,
            animationSpec = activeSpringDp,
            label = "shadowOffsetY",
        )
    val animatedInnerShadowRadius by
        animateDpAsState(
            targetValue = if (isHighlighterActive) expandedShadow.innerRadius else unexpandedShadow.innerRadius,
            animationSpec = activeSpringDp,
            label = "innerShadowR",
        )
    val animatedInnerShadowAlpha by
        animateFloatAsState(
            targetValue = if (isHighlighterActive) expandedShadow.innerAlpha else unexpandedShadow.innerAlpha,
            animationSpec = activeSpringFloat,
            label = "innerShadowA",
        )
    val animatedInnerShadowOffsetY by
        animateDpAsState(
            targetValue = if (isHighlighterActive) expandedShadow.innerOffsetY else unexpandedShadow.innerOffsetY,
            animationSpec = activeSpringDp,
            label = "innerShadowOffsetY",
        )

    val currentShadowProperties = remember(
        animatedShadowRadius,
        animatedShadowAlpha,
        animatedShadowOffsetY,
        animatedInnerShadowRadius,
        animatedInnerShadowAlpha,
        animatedInnerShadowOffsetY
    ) {
        LiquidGlassShadowProperties(
            radius = animatedShadowRadius,
            color = Color.Black,
            alpha = animatedShadowAlpha,
            offset = DpOffset(0.dp, animatedShadowOffsetY),
            innerRadius = animatedInnerShadowRadius,
            innerColor = Color.Black,
            innerAlpha = animatedInnerShadowAlpha,
            innerOffset = DpOffset(0.dp, animatedInnerShadowOffsetY)
        )
    }

    // --- Dynamic blur on interact / slide ---
    val animatedBlur by
        animateDpAsState(
            targetValue = if (isHighlighterActive) 1.5.dp else 0.dp,
            animationSpec = activeSpringDp,
            label = "blur",
        )

    // --- Dynamic specular glare boost on interact ---
    val animatedGlareAlpha by
        animateFloatAsState(
            targetValue = if (isInteracting) 0.85f else LiquidGlassTokens.GlareRestAlpha,
            animationSpec = bulgeSpringFloat,
            label = "glareA",
        )

    // --- Dynamic Highlighter Tint (Exact 1:1 match to Apple reference) ---
    val hlRestTint = if (isDark) Color.White else Color.Black
    val hlActiveTint = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.White

    val animatedTint by
        animateColorAsState(
            targetValue = if (isHighlighterActive) hlActiveTint else hlRestTint,
            animationSpec = tween(300),
            label = "tint",
        )

    val hlRestAlpha = if (isDark) 1f else 0.68f
    val hlActiveAlpha = if (isDark) 0.1f else 0.78f

    val animatedTintAlpha by
        animateFloatAsState(
            targetValue = if (isHighlighterActive) hlActiveAlpha else hlRestAlpha,
            animationSpec = tween(300),
            label = "tintA",
        )

    val localControlBackdrop = rememberLayerBackdrop()
    val pillShape = RoundedCornerShape(28.dp)

    var currentScale by remember { mutableFloatStateOf(1f) }
    var currentTx by remember { mutableFloatStateOf(0f) }
    var currentTy by remember { mutableFloatStateOf(0f) }

    Box(
        modifier =
            modifier
                .size(totalWidth, visibleHeight)
                .graphicsLayer { clip = false }
                .bubbleFluidity(
                    targetScale = 0.96f,
                    pullFactor = 0.04f,
                    onPhysicsUpdated = { s, tx, ty ->
                        currentScale = s
                        currentTx = tx
                        currentTy = ty
                    },
                )
                .pointerInput(items.size) {
                    val touchSlopPx = viewConfiguration.touchSlop
                    awaitPointerEventScope {
                        var startX = 0f
                        var wasTouching = false
                        var dragActivated = false
                        var startedOnHighlighter = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue

                            if (change.pressed) {
                                if (!wasTouching) {
                                    startX = change.position.x
                                    dragActivated = false

                                    // Check if touch started on the highlighter
                                    val currentCenterPx = centerX.value.dp.toPx()
                                    val widthPx = highlighterWidth.toPx()
                                    val touchPadding = 16.dp.toPx()
                                    val leftBound = currentCenterPx - (widthPx / 2f) - touchPadding
                                    val rightBound = currentCenterPx + (widthPx / 2f) + touchPadding
                                    startedOnHighlighter = startX in leftBound..rightBound
                                }
                                wasTouching = true

                                if (
                                    startedOnHighlighter &&
                                        !dragActivated &&
                                        abs(change.position.x - startX) > touchSlopPx
                                ) {
                                    dragActivated = true
                                }

                                if (dragActivated) {
                                    dragX = change.position.x
                                }
                            } else {
                                val currentDragX = dragX
                                if (dragActivated && currentDragX != null) {
                                    val itemWidthPx = itemWidth.toPx()
                                    val dropIndex =
                                        (currentDragX / itemWidthPx).toInt().coerceIn(0, items.size - 1)
                                    if (dropIndex != selectedIndex) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        items[dropIndex].onClick()
                                    }
                                }
                                wasTouching = false
                                dragX = null
                                dragActivated = false
                                startedOnHighlighter = false
                            }
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // 1. CAPTURED LAYER (Base Layer + Board + Labels/Icons)
        Box(
            modifier =
                Modifier.requiredSize(totalWidth, samplingHeight)
                    .graphicsLayer { clip = false }
                    .layerBackdrop(localControlBackdrop),
            contentAlignment = Alignment.Center,
        ) {
            // STATIC Base Layer: Inverse-scaled to cancel out bubbleFluidity
            if (backdrop != null) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                val invScale = if (currentScale > 0f) 1f / currentScale else 1f
                                scaleX = invScale
                                scaleY = invScale
                                translationX = -currentTx
                                translationY = -currentTy
                            }
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                effects = {},
                                highlight = { null },
                                shadow = { null },
                                innerShadow = { null },
                                onDrawSurface = {},
                            )
                )
            }

            val morphProgress = (expansionFraction / 0.45f).coerceIn(0f, 1f)
            val easeProgress = FastOutSlowInEasing.transform(morphProgress)

            val solidActionColor = if (isDark) Color.White else Color.Black
            val boardExpandedTint = Color.Black // Both light & dark mode use smoked dark glass track
            val currentSurfaceTint = lerpColor(solidActionColor, boardExpandedTint, easeProgress)

            val boardRestAlpha = if (isDark) 0.88f else 0.92f
            val boardExpandedAlpha = if (isDark) 0.60f else 0.40f
            val currentSurfaceTintAlpha = lerpFloat(boardRestAlpha, boardExpandedAlpha, easeProgress)

            // Shadow is ONLY applied to the navbar when expanded
            val currentShadowRadius = LiquidGlassTokens.ExpandedSearchShadowRadius * easeProgress
            val currentShadowOffsetY = LiquidGlassTokens.ExpandedSearchShadowOffset.y * easeProgress
            val currentShadowOffset = DpOffset(0.dp, currentShadowOffsetY)
            val currentShadowColor = LiquidGlassTokens.ExpandedSearchShadowColor.copy(
                alpha = LiquidGlassTokens.ExpandedSearchShadowColor.alpha * easeProgress
            )

            // Board: Authentic Liquid Glass Panel (Active refractions across all states)
            if (backdrop != null) {
                LiquidGlassPanel(
                    backdrop = backdrop,
                    modifier = Modifier.size(totalWidth, visibleHeight),
                    shape = pillShape,
                    config =
                        LiquidGlassPresets.NavBar.copy(
                            shape = pillShape,
                            surfaceTint = currentSurfaceTint,
                            surfaceTintAlpha = currentSurfaceTintAlpha,
                            shadowRadius = currentShadowRadius,
                            shadowOffset = currentShadowOffset,
                            shadowColor = currentShadowColor,
                        ),
                    content = {},
                )
            } else {
                Surface(
                    modifier = Modifier.size(totalWidth, visibleHeight),
                    shape = pillShape,
                    color = currentSurfaceTint.copy(alpha = currentSurfaceTintAlpha),
                    shadowElevation = 12.dp * easeProgress,
                    content = {},
                )
            }

            // Action Text inside the board (shows "Pair Device" / "Send File" at rest)
            if (actionText != null && morphProgress < 0.60f) {
                val actionAlpha = (1f - (morphProgress / 0.28f)).coerceIn(0f, 1f)
                val actionEase = FastOutSlowInEasing.transform(actionAlpha)
                val actionTextColor = if (isDark) Color.Black else Color.White
                val actionYOffset = -14.dp * (1f - actionEase)

                Box(
                    modifier =
                        Modifier.size(totalWidth, visibleHeight)
                            .graphicsLayer {
                                alpha = actionAlpha
                                translationY = actionYOffset.toPx()
                            }
                            .clickable(enabled = expansionFraction <= 0.05f) {
                                onActionClick?.invoke()
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = actionText,
                        color = actionTextColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }

            // Tabs Content (Staggers in from the bottom/top without scaling as sheet extends)
            if (morphProgress > 0.15f) {
                val tabsBounceFraction = ((morphProgress - 0.18f) / 0.55f).coerceIn(0f, 1f)
                Row(
                    modifier =
                        Modifier.size(totalWidth, visibleHeight)
                            .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val tabStagger = index * 0.06f
                        val itemProgress = ((tabsBounceFraction - tabStagger) / 0.76f).coerceIn(0f, 1f)
                        val tabEase = FastOutSlowInEasing.transform(itemProgress)
                        // Stagger in vertically from bottom (no scale-in)
                        val itemYOffset = 20.dp * (1f - tabEase)

                        SegmentedTabItem(
                            item = item,
                            onPressedChanged = { isPressed ->
                                pressedIndex = if (isPressed) index else null
                            },
                            modifier =
                                Modifier.weight(1f)
                                    .graphicsLayer {
                                        alpha = tabEase
                                        translationY = itemYOffset.toPx()
                                    },
                        )
                    }
                }
            }
        }

        // 2. HIGHLIGHTER (Draws on top, samples captured layer, refracting board and labels with bulge)
        val morphProgress = (expansionFraction / 0.45f).coerceIn(0f, 1f)
        if (morphProgress > 0.15f) {
            val tabsBounceFraction = ((morphProgress - 0.18f) / 0.55f).coerceIn(0f, 1f)
            val highlighterEase = FastOutSlowInEasing.transform(tabsBounceFraction)
            val hlYOffset = 10.dp * (1f - highlighterEase)

            Box(
                modifier =
                    Modifier.requiredSize(totalWidth, samplingHeight).graphicsLayer {
                        clip = false
                    }
            ) {
                LiquidGlassPanel(
                    backdrop = localControlBackdrop,
                    modifier =
                        Modifier.align(Alignment.CenterStart)
                            .size(highlighterWidth, highlighterHeight)
                            .graphicsLayer {
                                val centerPx = with(density) { centerX.value.dp.toPx() }
                                val widthPx = highlighterWidth.toPx()
                                translationX = centerPx - (widthPx / 2f)
                                translationY = hlYOffset.toPx()
                                alpha = highlighterEase
                                clip = false
                            }
                            .background(
                                color = animatedTint.copy(alpha = animatedTintAlpha),
                                shape = pillShape,
                            )
                            .zIndex(10f),
                    shape = pillShape,
                    config =
                        LiquidGlassPresets.IconButton
                            .withShadowProperties(currentShadowProperties)
                            .copy(
                                shape = pillShape,
                                blurRadius = animatedBlur,
                                lensHeight = animatedLensHeight,
                                lensAmount = animatedLensAmount,
                                surfaceTint = animatedTint,
                                surfaceTintAlpha = animatedTintAlpha,
                                restRefraction = animatedRefraction,
                                depthEffect = true,
                                glareFactor = animatedGlareAlpha * 100f,
                            ),
                    content = {},
                )
            }
        }
    }
}

/**
 * Interactive Tab Item inside the segmented control with icon positioned above label.
 */
@Composable
private fun SegmentedTabItem(
    item: SegmentedControlItem,
    onPressedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(isPressed) { onPressedChanged(isPressed) }

    val iconColor by
        animateColorAsState(
            targetValue =
                if (item.isSelected) {
                    if (isDark) Color.White else MaterialTheme.colorScheme.primary
                } else {
                    if (isDark) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.90f)
                },
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "segIconColor",
        )

    val labelColor by
        animateColorAsState(
            targetValue =
                if (item.isSelected) {
                    if (isDark) Color.White else MaterialTheme.colorScheme.primary
                } else {
                    if (isDark) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.90f)
                },
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "segLabelColor",
        )

    val iconScale by animateFloatAsState(
        targetValue = if (item.isSelected) DynamicFluidityConfig.Default.pressScale else 1.0f,
        animationSpec = DynamicMotionConfig.Default.springSpec(isExpanded = item.isSelected),
        label = "segIconScale"
    )

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(CircleShape)
                .bubbleFluidity(targetScale = 0.90f, pullFactor = 0.08f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (!item.isSelected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        item.onClick()
                    },
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Text(
            text = item.title,
            color = labelColor,
            fontSize = 12.5.sp,
            fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = (-0.2).sp,
            maxLines = 1,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun LiquidGlassSegmentedControlPreview() {
    var selectedTab by remember { mutableStateOf(0) }
    val items =
        listOf(
            SegmentedControlItem(
                title = "Photos",
                icon = MaterialSymbols.Photo,
                isSelected = selectedTab == 0,
                onClick = { selectedTab = 0 },
            ),
            SegmentedControlItem(
                title = "Audio",
                icon = MaterialSymbols.MusicNote,
                isSelected = selectedTab == 1,
                onClick = { selectedTab = 1 },
            ),
            SegmentedControlItem(
                title = "Files",
                icon = MaterialSymbols.Folder,
                isSelected = selectedTab == 2,
                onClick = { selectedTab = 2 },
            ),
            SegmentedControlItem(
                title = "History",
                icon = MaterialSymbols.History,
                isSelected = selectedTab == 3,
                onClick = { selectedTab = 3 },
            ),
        )

    DeXTheme {
        Box(
            modifier = Modifier.padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassSegmentedControl(
                items = items,
                totalWidth = 300.dp,
                visibleHeight = 58.dp,
            )
        }
    }
}
