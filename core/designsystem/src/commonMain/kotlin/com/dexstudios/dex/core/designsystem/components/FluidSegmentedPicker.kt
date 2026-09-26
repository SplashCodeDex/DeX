package com.dexstudios.dex.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.theme.DarkBackground
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class SegmentOption<T>(val value: T, val label: String, val icon: Painter? = null)

/**
 * FluidSegmentedPicker:
 * A modern, spring-sliding segmented pill control for Desktop DeX.
 *
 * Features:
 * - Fluid jelly wobble kinematics (velocity squish/stretch and out-of-phase scale springs)
 * - Drag-to-scrub gesture interaction with live cursor tracking, magnetic slot snapping, and fling physics
 * - Interactive ambient spotlight on the track board underneath the active indicator
 * - Soft elevated active indicator with subtle shadow and directional glare
 * - Accessible click targets and hover affordance (hand cursor)
 * - Harmonious light & dark contrast tokens
 */
@Composable
fun <T> FluidSegmentedPicker(options: List<SegmentOption<T>>, selectedOption: T, onOptionSelected: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (options.isEmpty()) return

    val selectedIndex = options.indexOfFirst { it.value == selectedOption }.coerceAtLeast(0)
    val cornerRadius = 10.dp
    val outerPadding = 3.dp

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val containerBg = if (isDark) {
        Color(0xFF22242A)
    } else {
        Color(0xFFE4E7F0)
    }
    val containerBorder = if (isDark) {
        Color(0xFF33363F)
    } else {
        Color(0xFFD0D4E0)
    }

    val activePillBg = if (isDark) {
        Color(0xFF363942)
    } else {
        Color(0xFFFFFFFF)
    }
    val activePillBorder = if (isDark) {
        Color(0xFF4A4E5A)
    } else {
        Color(0xFFCBD0DC)
    }

    BoxWithConstraints(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerBg)
            .border(1.dp, containerBorder, RoundedCornerShape(cornerRadius))
            .shinyGlare(shape = RoundedCornerShape(cornerRadius))
            .padding(outerPadding),
    ) {
        val density = LocalDensity.current
        val coroutineScope = rememberCoroutineScope()

        val availableWidth = maxWidth
        val segmentCount = options.size
        val segmentWidth = availableWidth / segmentCount
        val targetIndicatorOffset = segmentWidth * selectedIndex

        val offsetAnimatable = remember { Animatable(targetIndicatorOffset.value) }
        val indicatorSlideSpring = remember { spring<Float>(dampingRatio = 0.55f, stiffness = 280f) }

        val velocityTracker = remember { VelocityTracker() }
        val velocityAnimatable = remember { Animatable(0f) }
        val velocitySpringSpec = remember { spring<Float>(dampingRatio = 0.5f, stiffness = 300f) }

        // Asymmetric out-of-phase scale oscillation for organic fluid squish/wobble
        val scaleXAnimatable = remember { Animatable(1f) }
        val scaleYAnimatable = remember { Animatable(1f) }
        val scaleXSpringSpec = remember { spring<Float>(dampingRatio = 0.6f, stiffness = 250f) }
        val scaleYSpringSpec = remember { spring<Float>(dampingRatio = 0.7f, stiffness = 250f) }

        val spotlightAlpha = remember { Animatable(0f) }
        val spotlightSpringSpec = remember { spring<Float>(dampingRatio = 0.5f, stiffness = 300f) }

        var isDragging by remember { mutableStateOf(false) }
        var isInitialComposition by remember { mutableStateOf(true) }

        LaunchedEffect(selectedIndex, targetIndicatorOffset.value) {
            if (isInitialComposition) {
                isInitialComposition = false
                offsetAnimatable.snapTo(targetIndicatorOffset.value)
                return@LaunchedEffect
            }
            if (isDragging) return@LaunchedEffect

            val startX = offsetAnimatable.value
            val targetX = targetIndicatorOffset.value
            val distance = targetX - startX
            if (abs(distance) > 0.5f) {
                val initialVelocity = (distance / availableWidth.value.coerceAtLeast(1f)).coerceIn(-1f, 1f) * 12f
                coroutineScope.launch {
                    velocityAnimatable.snapTo(initialVelocity)
                    velocityAnimatable.animateTo(0f, velocitySpringSpec)
                }
                coroutineScope.launch {
                    scaleXAnimatable.animateTo(1.08f, scaleXSpringSpec)
                    scaleXAnimatable.animateTo(1f, scaleXSpringSpec)
                }
                coroutineScope.launch {
                    scaleYAnimatable.animateTo(1.04f, scaleYSpringSpec)
                    scaleYAnimatable.animateTo(1f, scaleYSpringSpec)
                }
            }
            offsetAnimatable.animateTo(
                targetValue = targetX,
                animationSpec = indicatorSlideSpring,
            )
        }

        val isMoving = isDragging || offsetAnimatable.isRunning || velocityAnimatable.isRunning
        LaunchedEffect(isMoving) {
            if (isMoving) {
                spotlightAlpha.animateTo(1f, spotlightSpringSpec)
            } else {
                spotlightAlpha.animateTo(0f, spotlightSpringSpec)
            }
        }

        // Gesture Detection: Click-to-select and drag-to-scrub with magnetic snapping
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .pointerInput(enabled, options.size, availableWidth) {
                    if (!enabled) return@pointerInput
                    val touchSlop = viewConfiguration.touchSlop
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            velocityTracker.resetTracking()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)

                            var dragActivated = false
                            val pointerId = down.id

                            coroutineScope.launch {
                                scaleXAnimatable.animateTo(1.06f, scaleXSpringSpec)
                                scaleYAnimatable.animateTo(1.04f, scaleYSpringSpec)
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                if (!change.pressed) {
                                    // Pointer released
                                    if (dragActivated) {
                                        val velocityX = velocityTracker.calculateVelocity().x
                                        val currentOffsetPx = with(density) { offsetAnimatable.value.dp.toPx() }
                                        val segWidthPx = with(density) { segmentWidth.toPx() }
                                        val rawFraction = if (segWidthPx > 0f) currentOffsetPx / segWidthPx else 0f
                                        val flingThreshold = 350f

                                        val targetIndex = when {
                                            velocityX > flingThreshold -> (rawFraction.toInt() + 1).coerceAtMost(segmentCount - 1)
                                            velocityX < -flingThreshold -> rawFraction.toInt().coerceAtLeast(0)
                                            else -> rawFraction.roundToInt().coerceIn(0, segmentCount - 1)
                                        }

                                        val normalizedV = (velocityX / density.density / 100f).coerceIn(-6f, 6f)
                                        coroutineScope.launch {
                                            velocityAnimatable.snapTo(normalizedV)
                                            velocityAnimatable.animateTo(0f, velocitySpringSpec)
                                        }

                                        onOptionSelected(options[targetIndex].value)
                                        coroutineScope.launch {
                                            offsetAnimatable.animateTo(
                                                targetValue = (segmentWidth * targetIndex).value,
                                                animationSpec = indicatorSlideSpring,
                                            )
                                        }
                                    } else {
                                        // Simple tap / click without drag
                                        val segWidthPx = with(density) { segmentWidth.toPx() }
                                        val clickedIndex = if (segWidthPx > 0f) {
                                            (downPos.x / segWidthPx).toInt().coerceIn(0, segmentCount - 1)
                                        } else {
                                            0
                                        }
                                        onOptionSelected(options[clickedIndex].value)
                                    }

                                    isDragging = false
                                    coroutineScope.launch {
                                        scaleXAnimatable.animateTo(1f, scaleXSpringSpec)
                                        scaleYAnimatable.animateTo(1f, scaleYSpringSpec)
                                    }
                                    break
                                }

                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val dragDelta = change.position.x - downPos.x

                                if (!dragActivated && abs(dragDelta) > touchSlop) {
                                    dragActivated = true
                                    isDragging = true
                                }

                                if (dragActivated) {
                                    change.consume()
                                    val segWidthPx = with(density) { segmentWidth.toPx() }
                                    val availWidthPx = with(density) { availableWidth.toPx() }
                                    val minLeftPx = 0f
                                    val maxLeftPx = (availWidthPx - segWidthPx).coerceAtLeast(0f)
                                    val rawLeftPx = change.position.x - segWidthPx / 2f
                                    val maxOverdragPx = with(density) { 14.dp.toPx() }

                                    val targetLeftPx = when {
                                        rawLeftPx < minLeftPx -> minLeftPx - ((minLeftPx - rawLeftPx) * 0.35f).coerceAtMost(maxOverdragPx)
                                        rawLeftPx > maxLeftPx -> maxLeftPx + ((rawLeftPx - maxLeftPx) * 0.35f).coerceAtMost(maxOverdragPx)
                                        else -> rawLeftPx
                                    }
                                    val targetLeftDp = with(density) { targetLeftPx.toDp().value }
                                    coroutineScope.launch {
                                        offsetAnimatable.snapTo(targetLeftDp)
                                    }
                                }
                            }
                        }
                    }
                },
        )

        // Ambient interactive spotlight on the track underneath the active indicator
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    if (spotlightAlpha.value > 0.001f) {
                        val alpha = spotlightAlpha.value
                        val currentOffsetPx = with(density) { offsetAnimatable.value.dp.toPx() }
                        val segWidthPx = with(density) { segmentWidth.toPx() }
                        val centerOffset = Offset(currentOffsetPx + segWidthPx / 2f, size.height / 2f)
                        val spotlightRadius = size.height * 1.5f

                        // 1. Soft ambient wash across the track
                        drawRect(
                            color = Color.White.copy(alpha = (if (isDark) 0.04f else 0.06f) * alpha),
                            blendMode = BlendMode.Plus,
                        )
                        // 2. Focused radial spotlight centered at the active indicator pill
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.0f to Color.White.copy(alpha = (if (isDark) 0.18f else 0.25f) * alpha),
                                0.50f to Color.White.copy(alpha = (if (isDark) 0.07f else 0.10f) * alpha),
                                1.0f to Color.Transparent,
                                center = centerOffset,
                                radius = spotlightRadius,
                            ),
                            radius = spotlightRadius,
                            center = centerOffset,
                            blendMode = BlendMode.Plus,
                        )
                        // 3. Specular rim highlight on the track border near the active indicator
                        drawOutline(
                            outline = RoundedCornerShape(cornerRadius).createOutline(size, layoutDirection, this),
                            brush = Brush.radialGradient(
                                0.0f to Color.White.copy(alpha = (if (isDark) 0.40f else 0.55f) * alpha),
                                0.50f to Color.White.copy(alpha = (if (isDark) 0.12f else 0.18f) * alpha),
                                1.0f to Color.Transparent,
                                center = centerOffset,
                                radius = spotlightRadius * 0.9f,
                            ),
                            style = Stroke(width = 1.dp.toPx()),
                            blendMode = BlendMode.Plus,
                        )
                    }
                },
        )

        // Sliding Active Indicator Pill with Fluid Jelly Wobble
        Box(
            modifier = Modifier
                .offset(x = offsetAnimatable.value.dp)
                .width(segmentWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    val v = (velocityAnimatable.value / 10f).coerceIn(-0.25f, 0.25f)
                    val baseScaleX = scaleXAnimatable.value
                    val baseScaleY = scaleYAnimatable.value
                    scaleX = baseScaleX / (1f - (v * 0.75f).coerceIn(-0.20f, 0.20f))
                    scaleY = baseScaleY * (1f - (v * 0.25f).coerceIn(-0.20f, 0.20f))
                    clip = false
                }
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(cornerRadius - 2.dp),
                    spotColor = Color.Black.copy(alpha = 0.25f),
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                )
                .clip(RoundedCornerShape(cornerRadius - 2.dp))
                .background(activePillBg)
                .border(1.dp, activePillBorder, RoundedCornerShape(cornerRadius - 2.dp))
                .shinyGlare(shape = RoundedCornerShape(cornerRadius - 2.dp)),
        )

        // Segment Options Row
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    },
                    animationSpec = tween(180),
                    label = "segmentedText_$index",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                    )
                }
            }
        }
    }
}
