package com.dexstudios.dex.core.designsystem.components.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.icons.DeXIcons
import com.dexstudios.dex.core.designsystem.theme.OverlayPhysics
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * Full-content expandable view with drag pill handle and back navigation.
 */
@Composable
fun StackedScreen(
    title: String,
    modifier: Modifier = Modifier,
    width: Dp = OverlayPhysics.STACKED_SCREEN_MAX_WIDTH,
    height: Dp = OverlayPhysics.STACKED_SCREEN_MAX_HEIGHT,
    subtitle: String? = null,
    showBackButton: Boolean = true,
    trailingHeaderAction: @Composable (() -> Unit)? = null,
    onBack: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    onHoverChanged: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }

    FluidOverlaySurface(
        modifier = modifier
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .graphicsLayer {
                val dragProgress = (dragOffsetY.value / 300f).coerceIn(0f, 1f)
                alpha = 1f - (dragProgress * 0.6f)
                scaleX = 1f - (dragProgress * 0.05f)
                scaleY = 1f - (dragProgress * 0.05f)
            },
        targetWidth = width,
        targetHeight = height,
        enableSwipeToDismiss = false, // Drag-down handle handles dismiss
        showHoverCloseButton = false,
        onDismiss = onDismiss ?: onBack,
        onHoverChanged = onHoverChanged,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Drag Pill Handle (tactile iOS sheet handle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                if (dragOffsetY.value > 100f) {
                                    coroutineScope.launch {
                                        dragOffsetY.animateTo(600f, OverlayPhysics.ExitTween)
                                        onBack()
                                    }
                                } else {
                                    coroutineScope.launch {
                                        dragOffsetY.animateTo(0f, OverlayPhysics.DragSnapBackFloatSpring)
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(0f, OverlayPhysics.DragSnapBackFloatSpring)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount.y > 0 || dragOffsetY.value > 0) {
                                    coroutineScope.launch {
                                        val newY = (dragOffsetY.value + dragAmount.y * 0.6f).coerceAtLeast(0f)
                                        dragOffsetY.snapTo(newY)
                                    }
                                }
                            },
                        )
                    }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation Header Row
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBackButton) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onBack,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(DeXIcons.ArrowBack),
                            contentDescription = "Back",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (trailingHeaderAction != null) {
                    trailingHeaderAction()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Body Content Slot
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                content()
            }
        }
    }
}
