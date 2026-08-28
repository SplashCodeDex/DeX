package com.dexstudios.dex.core.designsystem.components.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_close
import com.dexstudios.dex.core.designsystem.theme.OverlayPhysics
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Wrapper for individual items hosted in the [FluidNotificationStack].
 */
data class StackItem<T>(val id: String, val data: T, val content: @Composable (data: T, isTop: Boolean, onDismiss: () -> Unit) -> Unit)

/**
 * Interactive Apple-grade fluid card stack compositor:
 * - Resting state: Top card in front; up to 2 peeking shelves beneath (8dp offset, 0.96f scale).
 * - Upward Spring Fan-Out: On hover, cards smoothly spring unstack upwards into a vertical column
 *   with 12dp gaps via continuous animated layout interpolation (zero jumping or layout popping).
 * - High-Volume Capping: Limits visible fan-out to [maxVisibleCards] (default 5).
 * - Interactive Backlog Pill: Renders a "+N more • Clear All" pill when a large queue is fanned out.
 * - Flush Right Alignment: All cards align flush against the right margin.
 */
@Composable
fun <T> FluidNotificationStack(
    items: List<StackItem<T>>,
    modifier: Modifier = Modifier,
    fanUpwards: Boolean = true,
    alignRight: Boolean = true,
    maxVisibleCards: Int = 5,
    onDismissItem: (String) -> Unit,
    onClearAll: (() -> Unit)? = null,
    onStackHoverChanged: ((Boolean) -> Unit)? = null,
) {
    var isStackHovered by remember { mutableStateOf(false) }

    val visibleItems = remember(items, maxVisibleCards) { items.take(maxVisibleCards) }
    val backlogCount = remember(items, maxVisibleCards) { (items.size - maxVisibleCards).coerceAtLeast(0) }

    // Smooth hover progress (0.0f = resting stack, 1.0f = fully fanned out column)
    val hoverProgress by animateFloatAsState(
        targetValue = if (isStackHovered && items.size > 1) 1.0f else 0.0f,
        animationSpec = OverlayPhysics.StackFanFloatSpring,
        label = "stackHoverProgress",
    )

    Box(
        modifier = modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter, PointerEventType.Move -> {
                                if (!isStackHovered) {
                                    isStackHovered = true
                                    onStackHoverChanged?.invoke(true)
                                }
                            }

                            PointerEventType.Exit -> {
                                if (isStackHovered) {
                                    isStackHovered = false
                                    onStackHoverChanged?.invoke(false)
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = if (fanUpwards) Alignment.BottomEnd else Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier.wrapContentSize(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top Backlog Pill (When fanUpwards is true and backlog exists)
            if (fanUpwards && backlogCount > 0 && hoverProgress > 0.05f) {
                BacklogSummaryPill(
                    backlogCount = backlogCount,
                    alpha = hoverProgress,
                    onClearAll = onClearAll,
                )
            }

            // Continuous Kinematic Layout interpolating between collapsed deck & fanned column
            Layout(
                content = {
                    visibleItems.forEachIndexed { index, item ->
                        val isTop = index == 0
                        item.content(item.data, isTop) {
                            onDismissItem(item.id)
                        }
                    }
                },
            ) { measurables, constraints ->
                val placeables = measurables.map { measurable ->
                    measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                }

                val count = placeables.size
                if (count == 0) {
                    return@Layout layout(0, 0) {}
                }

                val peekOffsetPx = OverlayPhysics.STACK_PEEK_OFFSET_DP.toPx()
                val fanGapPx = OverlayPhysics.STACK_FAN_GAP_DP.toPx()

                // 1. Calculate Resting Dimensions & Positions
                val restingWidth = placeables[0].width
                val maxVisiblePeeks = OverlayPhysics.MAX_VISIBLE_PEEKS // 2
                val visiblePeeksCount = (count - 1).coerceIn(0, maxVisiblePeeks)
                val restingHeight = placeables[0].height + (visiblePeeksCount * peekOffsetPx).roundToInt()

                // 2. Calculate Fanned-Out Dimensions & Positions
                var fannedWidth = 0
                var totalContentHeight = 0

                placeables.forEachIndexed { _, p ->
                    fannedWidth = max(fannedWidth, p.width)
                    totalContentHeight += p.height
                }
                val totalGapsHeight = ((count - 1) * fanGapPx).roundToInt()
                val fannedHeight = totalContentHeight + totalGapsHeight

                val fannedYPositions = IntArray(count)
                if (fanUpwards) {
                    // Item 0 is at bottom, older items fan out upwards above it
                    var currentBottom = fannedHeight
                    for (i in 0 until count) {
                        val p = placeables[i]
                        fannedYPositions[i] = currentBottom - p.height
                        currentBottom -= (p.height + fanGapPx.roundToInt())
                    }
                } else {
                    // Item 0 is at top, older items fan out downwards below it
                    var currentTop = 0
                    for (i in 0 until count) {
                        val p = placeables[i]
                        fannedYPositions[i] = currentTop
                        currentTop += (p.height + fanGapPx.roundToInt())
                    }
                }

                // 3. Interpolate Layout Dimensions
                val currentLayoutWidth = lerp(restingWidth.toFloat(), fannedWidth.toFloat(), hoverProgress).roundToInt()
                val currentLayoutHeight = lerp(restingHeight.toFloat(), fannedHeight.toFloat(), hoverProgress).roundToInt()

                layout(currentLayoutWidth, currentLayoutHeight) {
                    // Place back-to-front so Item 0 (top/latest) is drawn on top when overlapping
                    for (i in count - 1 downTo 0) {
                        val p = placeables[i]

                        // Resting Y:
                        // Upward peeks (Windows): Item 0 at bottom, Item 1 at -8dp, Item 2 at -16dp
                        // Downward peeks (macOS): Item 0 at top, Item 1 at +8dp, Item 2 at +16dp
                        val restingY = if (fanUpwards) {
                            (visiblePeeksCount - i.coerceAtMost(maxVisiblePeeks)) * peekOffsetPx
                        } else {
                            i.coerceAtMost(maxVisiblePeeks) * peekOffsetPx
                        }
                        val fannedY = fannedYPositions[i].toFloat()
                        val currentY = lerp(restingY, fannedY, hoverProgress).roundToInt()

                        // Resting Scale: Level 0 -> 1.0f, Level 1 -> 0.96f, Level 2 -> 0.92f, Level 3+ -> 0.88f
                        val restingScale = if (i <= maxVisiblePeeks) {
                            1.0f - (i * OverlayPhysics.STACK_PEEK_SCALE_STEP)
                        } else {
                            1.0f - ((maxVisiblePeeks + 1) * OverlayPhysics.STACK_PEEK_SCALE_STEP)
                        }
                        val currentScale = lerp(restingScale, 1.0f, hoverProgress)

                        // Resting Alpha: Level 0 -> 1.0f, Level 1 -> 0.88f, Level 2 -> 0.76f, Level 3+ -> 0.0f
                        val restingAlpha = if (i <= maxVisiblePeeks) {
                            1.0f - (i * OverlayPhysics.STACK_PEEK_ALPHA_STEP)
                        } else {
                            0.0f
                        }
                        val currentAlpha = lerp(restingAlpha, 1.0f, hoverProgress).coerceIn(0.0f, 1.0f)

                        val currentX = if (alignRight) {
                            currentLayoutWidth - p.width
                        } else {
                            (currentLayoutWidth - p.width) / 2
                        }

                        p.placeWithLayer(
                            x = currentX,
                            y = currentY,
                            zIndex = (count - i).toFloat(),
                            layerBlock = {
                                scaleX = currentScale
                                scaleY = currentScale
                                alpha = currentAlpha
                                clip = false
                                transformOrigin = if (alignRight) TransformOrigin(1.0f, 0.5f) else TransformOrigin(0.5f, 0.5f)
                            },
                        )
                    }
                }
            }

            // Bottom Backlog Pill (When fanUpwards is false and backlog exists)
            if (!fanUpwards && backlogCount > 0 && hoverProgress > 0.05f) {
                BacklogSummaryPill(
                    backlogCount = backlogCount,
                    alpha = hoverProgress,
                    onClearAll = onClearAll,
                )
            }
        }

        // Overflow Indicator Badge (+N) in Resting Stack
        if (items.size > OverlayPhysics.MAX_VISIBLE_PEEKS + 1) {
            val overflowCount = items.size - (OverlayPhysics.MAX_VISIBLE_PEEKS + 1)
            val badgeAlpha = (1.0f - hoverProgress).coerceIn(0.0f, 1.0f)

            if (badgeAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 6.dp, end = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .zIndex(100f),
                ) {
                    Text(
                        text = "+$overflowCount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = badgeAlpha),
                    )
                }
            }
        }
    }
}

/**
 * Interactive summary pill displayed when hovering a large backlog queue.
 */
@Composable
private fun BacklogSummaryPill(backlogCount: Int, alpha: Float, onClearAll: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .bubbleFluidity(targetScale = 0.96f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .zIndex(200f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "+$backlogCount more in queue",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (onClearAll != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClearAll,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_fluent_close),
                    contentDescription = "Clear All",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = "Clear All",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
