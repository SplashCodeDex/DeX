package com.dexstudios.dex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.zIndex
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassTokens
import com.dexstudios.dex.ui.components.glass.shinyGlare
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
 * Implements the exact optical and physics pipeline from [FloatingPillNavBar]:
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

    // Add a directional peak shift towards the pressed tab (1:1 with FloatingPillNavBar)
    val peakShiftDp =
        if (isPeaking) {
            val pressedCenterDp =
                horizontalPadding + (itemWidth * (pressedIndex ?: selectedIndex)) + (itemWidth / 2f)
            val diff = pressedCenterDp - selectedCenterDp
            if (diff > 0.dp) 20.dp else -20.dp
        } else 0.dp

    // Stretch width to create a teardrop shape pointing towards the finger (1:1 with FloatingPillNavBar)
    val peakStretchDp = if (isPeaking) 24.dp else 0.dp

    // --- Highlighter dynamic sizing & signature bulge on interact ---
    // At rest: sits neatly aligned inside track slot (itemWidth - 6dp, visibleHeight - 8dp)
    // On interact: BULGES OUT vertically (+16dp over track) and expands horizontally
    val restWidth = (itemWidth - 6.dp).coerceAtLeast(40.dp)
    val interactWidth = itemWidth * 1.35f

    val highlighterWidth by
        animateDpAsState(
            targetValue = (if (isInteracting) interactWidth else restWidth) + peakStretchDp,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
            label = "hlW",
        )
    val highlighterHeight by
        animateDpAsState(
            targetValue = if (isInteracting) visibleHeight + 16.dp else visibleHeight - 8.dp,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
            label = "hlH",
        )

    // --- Dynamic lens & refraction warp on interact (1:1 with FloatingPillNavBar) ---
    val animatedLensHeight by
        animateDpAsState(
            targetValue = if (isInteracting) lensHeight else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "lensH",
        )
    val animatedLensAmount by
        animateDpAsState(
            targetValue = if (isInteracting) lensAmount else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "lensA",
        )
    val animatedRefraction by
        animateFloatAsState(
            targetValue = if (isInteracting) restRefraction else 0.20f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "refr",
        )

    // --- Elevation transition on interact (1:1 with FloatingPillNavBar) ---
    val animatedShadow by
        animateDpAsState(
            targetValue = if (isInteracting) 30.dp else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "shadow",
        )

    // --- Dynamic blur on interact (1:1 with FloatingPillNavBar) ---
    val animatedBlur by
        animateDpAsState(
            targetValue = if (isInteracting) 0.50.dp else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "blur",
        )

    // --- Inner shadow on interact (1:1 with FloatingPillNavBar) ---
    val animatedInnerShadowRadius by
        animateDpAsState(
            targetValue = if (isInteracting) 12.dp else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "innerShadowR",
        )
    val animatedInnerShadowAlpha by
        animateFloatAsState(
            targetValue = if (isInteracting) 0.45f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "innerShadowA",
        )

    // --- Highlighter position: drag follows finger, otherwise follows selected tab (with peak offset) ---
    val targetCenterDp by remember {
        derivedStateOf {
            val currentDragX = dragX
            if (currentDragX != null) {
                with(density) { currentDragX.toDp() }
            } else {
                val selectedCenter =
                    horizontalPadding + (itemWidth * selectedIndex) + (itemWidth / 2f)
                val isPeak = pressedIndex != null && pressedIndex != selectedIndex

                val shift =
                    if (isPeak) {
                        val pressedCenter =
                            horizontalPadding + (itemWidth * (pressedIndex ?: selectedIndex)) + (itemWidth / 2f)
                        val diff = pressedCenter - selectedCenter
                        if (diff > 0.dp) 20.dp else -20.dp
                    } else 0.dp

                selectedCenter + shift
            }
        }
    }

    val centerX = remember { Animatable(targetCenterDp.value) }

    LaunchedEffect(Unit) {
        snapshotFlow { Pair(targetCenterDp.value, dragX != null) }
            .collectLatest { (target, dragging) ->
                if (dragging) {
                    centerX.snapTo(target)
                } else {
                    centerX.animateTo(
                        targetValue = target,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f),
                    )
                }
            }
    }

    // --- Tint transitions: active while interacting OR still springing ---
    val isMoving = centerX.isRunning || abs(centerX.value - targetCenterDp.value) > 0.5f
    val isHighlighterActive = isInteracting || isMoving

    val animatedTint by
        animateColorAsState(
            targetValue =
                if (isHighlighterActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(300),
            label = "tint",
        )
    val animatedTintAlpha by
        animateFloatAsState(
            targetValue = if (isHighlighterActive) 0.15f else 0.20f,
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
                .size(totalWidth + 16.dp, visibleHeight + 12.dp)
                .bubbleFluidity(
                    targetScale = 0.95f,
                    pullFactor = 0.10f,
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
                                    val currentCenterPx = (centerX.value.dp + 8.dp).toPx()
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
                                    dragX = change.position.x - 8.dp.toPx()
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
                Modifier.requiredSize(totalWidth + 16.dp, samplingHeight)
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

            // Board
            if (backdrop != null) {
                LiquidGlassPanel(
                    backdrop = backdrop,
                    modifier = Modifier.size(totalWidth, visibleHeight),
                    shape = pillShape,
                    config =
                        LiquidGlassPresets.NavBar.copy(
                            shape = pillShape,
                            surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                            surfaceTintAlpha = 1f,
                        ),
                    content = {},
                )
            } else {
                Surface(
                    modifier = Modifier.size(totalWidth, visibleHeight),
                    shape = pillShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 12.dp,
                    content = {},
                )
            }

            // Tabs Content
            Row(
                modifier =
                    Modifier.size(totalWidth, visibleHeight)
                        .padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    SegmentedTabItem(
                        item = item,
                        onPressedChanged = { isPressed ->
                            pressedIndex = if (isPressed) index else null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 2. HIGHLIGHTER (Draws on top, samples captured layer, refracting board and labels with bulge)
        val indicatorOffset = centerX.value.dp - (highlighterWidth / 2f) + 8.dp

        Box(
            modifier =
                Modifier.requiredSize(totalWidth + 16.dp, samplingHeight).graphicsLayer {
                    clip = false
                }
        ) {
            LiquidGlassPanel(
                backdrop = localControlBackdrop,
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .size(highlighterWidth, highlighterHeight)
                        .graphicsLayer {
                            translationX = indicatorOffset.toPx()
                            clip = false
                        }
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = pillShape,
                        )
                        .shinyGlare(
                            shape = pillShape,
                            intensity = LiquidGlassTokens.GlareRestAlpha,
                        )
                        .zIndex(10f),
                shape = pillShape,
                config =
                    LiquidGlassPresets.IconButton.copy(
                        shape = pillShape,
                        blurRadius = animatedBlur,
                        lensHeight = animatedLensHeight,
                        lensAmount = animatedLensAmount,
                        surfaceTint = animatedTint,
                        surfaceTintAlpha = animatedTintAlpha,
                        restRefraction = animatedRefraction,
                        shadowRadius = animatedShadow,
                        depthEffect = true,
                        highlight =
                            LiquidGlassPresets.IconButton.highlight.copy(
                                alpha = LiquidGlassTokens.GlareRestAlpha
                            ),
                        innerShadow =
                            InnerShadow(
                                radius = animatedInnerShadowRadius,
                                color = Color.Black.copy(alpha = animatedInnerShadowAlpha),
                                offset = DpOffset(0.dp, 6.dp),
                            ),
                    ),
                content = {},
            )
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
                if (item.isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
            label = "segIconColor",
        )

    val labelColor by
        animateColorAsState(
            targetValue =
                if (item.isSelected) {
                    if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                },
            label = "segLabelColor",
        )

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(CircleShape)
                .bubbleFluidity(targetScale = 1.12f, pullFactor = 0.04f)
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
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(1.dp))
        }

        Text(
            text = item.title,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium,
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
