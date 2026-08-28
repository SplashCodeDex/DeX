package com.dexstudios.dex.core.designsystem.components.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.icons.DeXIcons
import com.dexstudios.dex.core.designsystem.theme.OverlayPhysics
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Foundational surface atom for all DeX Overlay and Notification cards.
 *
 * Implements:
 * - 48dp Rounded Corner Shape (pillow-soft Apple silhouette)
 * - 96% opacity themed solid surface with 1dp outlineVariant border
 * - Directional [shinyGlare] lighting rim (no liquid glass, no blur sampling, no glow)
 * - [bubbleFluidity] interactive press squish and bounce
 * - Elastic pull-to-dismiss gesture with 0.5x resistance and spring snap-back / fling
 * - Hover detection for auto-dismiss timer pause and hover close (X) button reveal
 * - Dynamic animated width and height morphing
 */
@Composable
fun FluidOverlaySurface(
    modifier: Modifier = Modifier,
    targetWidth: Dp? = null,
    targetHeight: Dp? = null,
    shape: Shape = RoundedCornerShape(OverlayPhysics.CORNER_RADIUS),
    surfaceColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = OverlayPhysics.SURFACE_ALPHA),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    enableGlare: Boolean = true,
    enableBubblePress: Boolean = true,
    enableSwipeToDismiss: Boolean = true,
    showHoverCloseButton: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    onHoverChanged: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var isHovered by remember { mutableStateOf(false) }

    // Dynamic morphing width & height
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth ?: Dp.Unspecified,
        animationSpec = OverlayPhysics.SizeMorphDpSpring,
        label = "surfaceWidth",
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight ?: Dp.Unspecified,
        animationSpec = OverlayPhysics.SizeMorphDpSpring,
        label = "surfaceHeight",
    )

    // Gesture translation along X axis with elastic spring snap
    val dragOffsetX = remember { Animatable(0f) }
    var isDismissingByGesture by remember { mutableStateOf(false) }

    val closeButtonAlpha by animateFloatAsState(
        targetValue = if (isHovered && showHoverCloseButton && onDismiss != null) 1f else 0f,
        animationSpec = OverlayPhysics.EnterFloatSpring,
        label = "closeButtonAlpha",
    )

    // Base sizing modifier
    val sizeModifier = Modifier
        .then(if (targetWidth != null) Modifier.width(animatedWidth) else Modifier)
        .then(if (targetHeight != null) Modifier.height(animatedHeight) else Modifier)

    Box(
        modifier = modifier
            .then(
                if (enableBubblePress) {
                    Modifier.bubbleFluidity(targetScale = 0.97f, pullFactor = 0.04f)
                } else {
                    Modifier
                },
            )
            .then(sizeModifier)
            .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
            .graphicsLayer {
                // Card fades completely to 0.0f by 140dp of drag travel
                val fadeDistancePx = 140f * density
                val dragFraction = (abs(dragOffsetX.value) / fadeDistancePx).coerceIn(0f, 1f)
                alpha = (1f - dragFraction).coerceIn(0f, 1f)
                scaleX = 1f - (dragFraction * 0.08f)
                scaleY = 1f - (dragFraction * 0.08f)
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> {
                                isHovered = true
                                onHoverChanged?.invoke(true)
                            }

                            PointerEventType.Exit -> {
                                isHovered = false
                                onHoverChanged?.invoke(false)
                            }
                        }
                    }
                }
            }
            .then(
                if (enableSwipeToDismiss && onDismiss != null) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                val currentOffset = dragOffsetX.value
                                val thresholdPx = OverlayPhysics.DRAG_DISMISS_THRESHOLD_DP.toPx()
                                if (abs(currentOffset) >= thresholdPx) {
                                    isDismissingByGesture = true
                                    coroutineScope.launch {
                                        val flingTarget = if (currentOffset > 0) 180f * density else -180f * density
                                        dragOffsetX.animateTo(
                                            targetValue = flingTarget,
                                            animationSpec = OverlayPhysics.ExitTween,
                                        )
                                        onDismiss()
                                    }
                                } else {
                                    // Elastic snap-back to origin
                                    coroutineScope.launch {
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = OverlayPhysics.DragSnapBackFloatSpring,
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragOffsetX.animateTo(0f, OverlayPhysics.DragSnapBackFloatSpring)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val current = dragOffsetX.value
                                    // Apply elastic tension multiplier
                                    val resistedDelta = dragAmount.x * OverlayPhysics.DRAG_RESISTANCE_MULTIPLIER
                                    dragOffsetX.snapTo(current + resistedDelta)
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(color = surfaceColor, shape = shape)
            .border(width = OverlayPhysics.BORDER_WIDTH, color = borderColor, shape = shape)
            .then(
                if (enableGlare) {
                    Modifier.shinyGlare(shape = shape, width = OverlayPhysics.BORDER_WIDTH)
                } else {
                    Modifier
                },
            ),
    ) {
        content()

        // Hover Close (X) Pill Button
        if (showHoverCloseButton && onDismiss != null) {
            val isPill = targetHeight != null && targetHeight <= 56.dp
            Box(
                modifier = Modifier
                    .align(if (isPill) Alignment.CenterEnd else Alignment.TopEnd)
                    .padding(
                        top = if (isPill) 0.dp else 18.dp,
                        end = if (isPill) 12.dp else 18.dp,
                    )
                    .size(24.dp)
                    .alpha(closeButtonAlpha)
                    .bubbleFluidity(targetScale = 0.92f, pullFactor = 0.05f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = org.jetbrains.compose.resources.painterResource(DeXIcons.Close),
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
