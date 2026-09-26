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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.dexstudios.dex.ui.components.glass.LiquidGlassShadowProperties
import com.dexstudios.dex.ui.components.glass.LiquidGlassTokens
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.theme.DeXTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import kotlin.math.sign
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.util.VelocityTracker

private const val SPOTLIGHT_SHADER_SRC = """
    uniform float2 size;
    layout(color) uniform half4 color;
    uniform float radius;
    uniform float2 position;

    half4 main(float2 coord) {
        float dist = distance(coord, position);
        float intensity = smoothstep(radius, radius * 0.5, dist);
        return color * intensity;
    }
"""

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

    // Subtle directional peak shift towards the pressed tab (within bounds)
    val peakShiftDp =
        if (isPeaking) {
            val pressedCenterDp =
                horizontalPadding + (itemWidth * (pressedIndex ?: selectedIndex)) + (itemWidth / 2f)
            val diff = pressedCenterDp - selectedCenterDp
            if (diff > 0.dp) 8.dp else -8.dp
        } else 0.dp

    // Stretch width subtly during peak interaction
    val peakStretchDp = if (isPeaking) 4.dp else 0.dp

    // --- Highlighter sizing: fixed slot dimensions matching GitHub app 1:1 (no bulging when scrolling) ---
    val highlighterWidth = (itemWidth - 6.dp).coerceAtLeast(36.dp)
    val highlighterHeight = (visibleHeight - 8.dp).coerceAtLeast(36.dp)

    val coroutineScope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }
    val velocityAnimatable = remember { Animatable(0f) }
    val velocitySpringSpec = remember { spring<Float>(dampingRatio = 0.5f, stiffness = 300f) }

    // Kinematic Springs: CodeDeX tuned signature overshoot springs
    val lensSlideSpring = remember { spring<Float>(dampingRatio = 0.5f, stiffness = 170f) }

    val spotlightShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(SPOTLIGHT_SHADER_SRC)
        } else null
    }

    // Boundary Overhang & Elastic Resistance (syncclipboard-xposed panelOffset):
    val firstSlotCenterDp = horizontalPadding + (itemWidth / 2f)
    val lastSlotCenterDp = horizontalPadding + (itemWidth * (items.size - 1)) + (itemWidth / 2f)

    val rawTargetCenterDp =
        if (dragX != null) {
            with(density) { dragX!!.toDp() }
        } else {
            selectedCenterDp + peakShiftDp
        }

    val targetCenterDp = rawTargetCenterDp.coerceIn(firstSlotCenterDp, lastSlotCenterDp)

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 6.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            val totalWidthPx = with(density) { totalWidth.toPx() }
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * FastOutSlowInEasing.transform(abs(fraction))
            }
        }
    }

    val pressProgressAnim = remember { Animatable(0f) }
    val scaleXAnim = remember { Animatable(1f) }
    val scaleYAnim = remember { Animatable(1f) }

    val pressProgressSpringSpec = remember { spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f) }
    val scaleXSpringSpec = remember { spring<Float>(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f) }
    val scaleYSpringSpec = remember { spring<Float>(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f) }
    val pressedScale = 78f / 56f

    val centerX = remember { Animatable(targetCenterDp.value) }

    val isMoving = centerX.isRunning || velocityAnimatable.isRunning
    val isHighlighterActive = isInteracting || isMoving

    // Interactive ambient spotlight on the track underneath the highlighter
    val spotlightAlpha = remember { Animatable(0f) }
    val spotlightSpringSpec = remember { spring<Float>(dampingRatio = 0.5f, stiffness = 300f) }

    LaunchedEffect(isHighlighterActive) {
        if (isHighlighterActive) {
            spotlightAlpha.animateTo(1f, spotlightSpringSpec)
        } else {
            spotlightAlpha.animateTo(0f, spotlightSpringSpec)
        }
    }

    LaunchedEffect(isInteracting) {
        if (isInteracting) {
            launch { pressProgressAnim.animateTo(1f, pressProgressSpringSpec) }
            launch { scaleXAnim.animateTo(pressedScale, scaleXSpringSpec) }
            launch { scaleYAnim.animateTo(pressedScale, scaleYSpringSpec) }
        } else {
            launch { pressProgressAnim.animateTo(0f, pressProgressSpringSpec) }
            launch { scaleXAnim.animateTo(1f, scaleXSpringSpec) }
            launch { scaleYAnim.animateTo(1f, scaleYSpringSpec) }
        }
    }

    LaunchedEffect(targetCenterDp.value, dragX != null) {
        if (dragX != null) {
            centerX.snapTo(targetCenterDp.value)
        } else {
            val startX = centerX.value
            val targetX = targetCenterDp.value
            val distance = targetX - startX
            if (abs(distance) > 1f) {
                // Initialize velocity in travel direction for fluid squish during slide
                val initialVelocity = (distance / totalWidth.value).coerceIn(-1f, 1f) * 12f
                coroutineScope.launch {
                    velocityAnimatable.snapTo(initialVelocity)
                    velocityAnimatable.animateTo(0f, velocitySpringSpec)
                }
                if (!isInteracting) {
                    coroutineScope.launch {
                        launch { pressProgressAnim.animateTo(1f, pressProgressSpringSpec) }
                        launch { scaleXAnim.animateTo(pressedScale, scaleXSpringSpec) }
                        launch { scaleYAnim.animateTo(pressedScale, scaleYSpringSpec) }
                        centerX.animateTo(
                            targetValue = targetCenterDp.value,
                            animationSpec = lensSlideSpring,
                        )
                        launch { pressProgressAnim.animateTo(0f, pressProgressSpringSpec) }
                        launch { scaleXAnim.animateTo(1f, scaleXSpringSpec) }
                        launch { scaleYAnim.animateTo(1f, scaleYSpringSpec) }
                    }
                    return@LaunchedEffect
                }
            }
            centerX.animateTo(
                targetValue = targetCenterDp.value,
                animationSpec = lensSlideSpring,
            )
        }
    }

    // --- Exact 1:1 GitHub app syncclipboard-xposed optical tokens ---
    val hlProgress = pressProgressAnim.value
    val hlLensHeight = 10.dp * hlProgress
    val hlLensAmount = 14.dp * hlProgress

    val currentHighlighterShadow = remember(hlProgress) {
        LiquidGlassShadowProperties(
            radius = 4.dp * hlProgress,
            color = Color.Black,
            alpha = 0.15f * hlProgress,
            offset = DpOffset(0.dp, 2.dp * hlProgress),
            innerRadius = 8.dp * hlProgress,
            innerColor = Color.Black,
            innerAlpha = 0.15f * hlProgress,
            innerOffset = DpOffset.Zero,
        )
    }

    val hlRestTint = if (!isDark) Color.Black else Color.White
    val hlRestAlpha = 0.10f * (1f - hlProgress)
    val hlActiveTint = Color.Black
    val hlActiveAlpha = 0.03f * hlProgress
    val hlCurrentTint = lerpColor(hlRestTint, hlActiveTint, hlProgress)
    val hlCurrentAlpha = hlRestAlpha + hlActiveAlpha

    val localControlBackdrop = rememberLayerBackdrop()
    val pillShape = RoundedCornerShape(percent = 50)

    Box(
        modifier =
            modifier
                .size(totalWidth, visibleHeight)
                .graphicsLayer { clip = false }
                .bubbleFluidity(
                    targetScale = 0.96f,
                    pullFactor = 0.04f,
                )
                .pointerInput(items.size) {
                    val touchSlopPx = viewConfiguration.touchSlop
                    awaitPointerEventScope {
                        var startX = 0f
                        var touchOffsetPx = 0f
                        var wasTouching = false
                        var dragActivated = false
                        var startedOnHighlighter = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue

                            if (change.pressed) {
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (!wasTouching) {
                                    startX = change.position.x
                                    val currentCenterPx = centerX.value.dp.toPx()
                                    touchOffsetPx = startX - currentCenterPx
                                    dragActivated = false

                                    // Check if touch started on the highlighter
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
                                    val currentX = change.position.x - touchOffsetPx
                                    val firstSlotPx = with(density) { firstSlotCenterDp.toPx() }
                                    val lastSlotPx = with(density) { lastSlotCenterDp.toPx() }
                                    val overdrag = when {
                                        currentX < firstSlotPx -> currentX - firstSlotPx
                                        currentX > lastSlotPx -> currentX - lastSlotPx
                                        else -> 0f
                                    }
                                    coroutineScope.launch {
                                        offsetAnimation.snapTo(overdrag)
                                    }
                                    dragX = currentX
                                    val currentV = (velocityTracker.calculateVelocity().x / density.density / 100f).coerceIn(-4f, 4f)
                                    coroutineScope.launch {
                                        velocityAnimatable.snapTo(currentV)
                                    }
                                }
                            } else {
                                coroutineScope.launch {
                                    offsetAnimation.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
                                }
                                val currentDragX = dragX
                                if (dragActivated && currentDragX != null) {
                                    val itemWidthPx = itemWidth.toPx()
                                    val dropIndex =
                                        (currentDragX / itemWidthPx).toInt().coerceIn(0, items.size - 1)
                                    if (dropIndex != selectedIndex) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        items[dropIndex].onClick()
                                    }
                                    val velocityX = velocityTracker.calculateVelocity().x
                                    val normalizedVelocity = (velocityX / density.density / 100f).coerceIn(-6f, 6f)
                                    coroutineScope.launch {
                                        velocityAnimatable.snapTo(normalizedVelocity)
                                        velocityAnimatable.animateTo(0f, velocitySpringSpec)
                                    }
                                }
                                velocityTracker.resetTracking()
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
        // 1. CAPTURED LAYER (Board + Interactive Spotlight + Labels/Icons)
        Box(
            modifier =
                Modifier.requiredSize(totalWidth, samplingHeight)
                    .graphicsLayer { clip = false }
                    .layerBackdrop(localControlBackdrop),
            contentAlignment = Alignment.Center,
        ) {
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

            // Board: Authentic Liquid Glass Panel with Interactive Ambient Spotlight & Specular Rim Highlight
            Box(
                modifier = Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .size(totalWidth, visibleHeight)
                    .drawWithContent {
                        drawContent()
                        if (spotlightAlpha.value > 0.001f) {
                            val alpha = spotlightAlpha.value
                            val centerPx = with(density) { centerX.value.dp.toPx() } + panelOffset
                            val centerOffset = Offset(centerPx.coerceIn(0f, size.width), size.height / 2f)
                            val spotlightRadius = size.height * 1.4f

                            val outline = pillShape.createOutline(size, layoutDirection, this)
                            val path = when (outline) {
                                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                                is Outline.Generic -> outline.path
                            }

                            // 1. Soft surface wash & focused radial spotlight on the board surface (AGSL RuntimeShader if API 33+)
                            clipPath(path) {
                                drawRect(
                                    color = Color.White.copy(alpha = (if (isDark) 0.035f else 0.06f) * alpha),
                                    blendMode = BlendMode.Plus,
                                )
                                if (spotlightShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    spotlightShader.setFloatUniform("size", size.width, size.height)
                                    spotlightShader.setColorUniform(
                                        "color",
                                        Color.White.copy(alpha = (if (isDark) 0.14f else 0.20f) * alpha).toArgb()
                                    )
                                    spotlightShader.setFloatUniform("radius", spotlightRadius)
                                    spotlightShader.setFloatUniform("position", centerOffset.x, centerOffset.y)
                                    drawRect(
                                        ShaderBrush(spotlightShader),
                                        blendMode = BlendMode.Plus
                                    )
                                } else {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            0.0f to Color.White.copy(alpha = (if (isDark) 0.18f else 0.25f) * alpha),
                                            0.45f to Color.White.copy(alpha = (if (isDark) 0.07f else 0.10f) * alpha),
                                            1.0f to Color.Transparent,
                                            center = centerOffset,
                                            radius = spotlightRadius,
                                        ),
                                        radius = spotlightRadius,
                                        center = centerOffset,
                                        blendMode = BlendMode.Plus,
                                    )
                                }
                            }

                            // 2. Specular rim highlight: illuminates the specular glass edge of the navboard around the highlighter
                            drawOutline(
                                outline = outline,
                                brush = Brush.radialGradient(
                                    0.0f to Color.White.copy(alpha = (if (isDark) 0.45f else 0.60f) * alpha),
                                    0.50f to Color.White.copy(alpha = (if (isDark) 0.15f else 0.22f) * alpha),
                                    1.0f to Color.Transparent,
                                    center = centerOffset,
                                    radius = spotlightRadius * 0.9f,
                                ),
                                style = Stroke(width = 1.25.dp.toPx()),
                                blendMode = BlendMode.Plus,
                            )
                        }
                    }
            ) {
                if (backdrop != null) {
                    LiquidGlassPanel(
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxSize(),
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
                        modifier = Modifier.fillMaxSize(),
                        shape = pillShape,
                        color = currentSurfaceTint.copy(alpha = currentSurfaceTintAlpha),
                        shadowElevation = 12.dp * easeProgress,
                        content = {},
                    )
                }
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
                            .graphicsLayer { translationX = panelOffset }
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
                val activeHighlighterBackdrop =
                    if (backdrop != null) rememberCombinedBackdrop(backdrop, localControlBackdrop)
                    else localControlBackdrop

                LiquidGlassPanel(
                    backdrop = activeHighlighterBackdrop,
                    modifier =
                        Modifier.align(Alignment.CenterStart)
                            .size(highlighterWidth, highlighterHeight)
                            .graphicsLayer {
                                val centerPx = with(density) { centerX.value.dp.toPx() } + panelOffset
                                val widthPx = highlighterWidth.toPx()
                                translationX = centerPx - (widthPx / 2f)
                                translationY = hlYOffset.toPx()
                                alpha = highlighterEase

                                // Exact fluid squish and scaling from syncclipboard-xposed:
                                val v = (velocityAnimatable.value / 10f)
                                scaleX = scaleXAnim.value / (1f - (v * 0.75f).coerceIn(-0.20f, 0.20f))
                                scaleY = scaleYAnim.value * (1f - (v * 0.25f).coerceIn(-0.20f, 0.20f))
                                clip = false
                            }
                            .zIndex(10f),
                    shape = pillShape,
                    config =
                        LiquidGlassPresets.IconButton
                            .withShadowProperties(currentHighlighterShadow)
                            .copy(
                                shape = pillShape,
                                blurRadius = 0.dp,
                                lensHeight = hlLensHeight,
                                lensAmount = hlLensAmount,
                                surfaceTint = hlCurrentTint,
                                surfaceTintAlpha = hlCurrentAlpha,
                                restRefraction = 1f,
                                depthEffect = true,
                                chromaticAberration = true,
                                glareFactor = 100f * hlProgress,
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
