package com.dexstudios.dex.window.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.window.DockedWindowStateController
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import java.awt.MouseInfo

/**
 * Tactile Drag Pill Handle with 3-Phase Gesture Engine:
 * - Phase 1: 5px Manhattan Deadzone accumulator (|dx| + |dy| >= 5px)
 * - Phase 2: High-DPI Cursor Tracking (delta / density) with 20px Magnetic Edge Snapping
 * - Phase 3: Release settle & Sanity off-screen grab clamping
 * - Double-Click Reset: 450ms atomic 2D animation to resting dock
 * - Pinned Location Shake: ±5px 3-cycle shake feedback when locked
 */
@Composable
fun DragPillHandle(
    controller: DockedWindowStateController,
    modifier: Modifier = Modifier,
    showPinButton: Boolean = true
) {
    val density = LocalDensity.current.density
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val pillAlpha by animateFloatAsState(
        targetValue = when {
            controller.isDragging -> 0.95f
            isHovered -> 0.85f
            else -> 0.40f
        },
        animationSpec = tween(150),
        label = "pillAlpha"
    )

    val pillScaleX by animateFloatAsState(
        targetValue = if (isHovered) 1.15f else 1.0f,
        animationSpec = tween(200, easing = DockCardPhysics.HoverEase),
        label = "pillScaleX"
    )

    val pillColor by animateColorAsState(
        targetValue = when {
            controller.isDragging -> MaterialTheme.colorScheme.primary
            isHovered -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "pillColor"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showPinButton) {
            // Pin Button
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (controller.isPinned) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { controller.isPinned = !controller.isPinned },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (controller.isPinned)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        // 3-Phase Drag Pill Container with Double-Click Reset & Pin Shake
        Box(
            modifier = Modifier
                .width(76.dp)
                .height(24.dp)
                .hoverable(interactionSource)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            controller.onDoubleTapReset()
                        }
                    )
                }
                .pointerInput(density) {
                    detectDragGestures(
                        onDragStart = {
                            val mouseLoc = try {
                                MouseInfo.getPointerInfo()?.location
                            } catch (_: Exception) {
                                null
                            }
                            if (mouseLoc != null) {
                                controller.onDragStart(mouseLoc.x, mouseLoc.y)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val mouseLoc = try {
                                MouseInfo.getPointerInfo()?.location
                            } catch (_: Exception) {
                                null
                            }
                            if (mouseLoc != null) {
                                controller.onDragMove(mouseLoc.x, mouseLoc.y, density)
                            } else {
                                val dpDx = dragAmount.x / density
                                val dpDy = dragAmount.y / density
                                controller.onDragDelta(dpDx, dpDy)
                            }
                        },
                        onDragEnd = {
                            controller.onDragEnd()
                        },
                        onDragCancel = {
                            controller.onDragEnd()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Visual pill bar
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .graphicsLayer {
                        scaleX = pillScaleX
                    }
                    .clip(CircleShape)
                    .background(pillColor.copy(alpha = pillAlpha))
            )
        }
    }
}
