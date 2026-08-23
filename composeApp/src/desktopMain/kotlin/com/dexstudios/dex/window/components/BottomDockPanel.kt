package com.dexstudios.dex.window.components
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_power_settings_new

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.profile_avatar
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import java.awt.Toolkit
import java.awt.event.InputEvent

enum class ExitConfirmationStage {
    Idle,
    Confirming
}

/**
 * BottomDockPanel:
 * - 34x34dp circular profile avatar button (opens Settings, scales to 0.6x during confirmation)
 * - 1dp horizontal accent divider
 * - 2-stage Exit Engine confirmation (Shift+Click bypass, active transfer detection, -62dp expansion, 3s auto-revert timer)
 */
@Composable
fun BottomDockPanel(
    onProfileClick: () -> Unit,
    onExitEngine: () -> Unit,
    hasActiveTransfers: Boolean = false,
    isMirroringActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var confirmationStage by remember { mutableStateOf(ExitConfirmationStage.Idle) }
    var isShiftHeld by remember { mutableStateOf(false) }

    // 3-second auto-revert timer
    LaunchedEffect(confirmationStage) {
        if (confirmationStage == ExitConfirmationStage.Confirming) {
            delay(3000)
            confirmationStage = ExitConfirmationStage.Idle
        }
    }

    // Kinematic Animations
    val isConfirming = confirmationStage == ExitConfirmationStage.Confirming

    val avatarScale by animateFloatAsState(
        targetValue = if (isConfirming) 0.6f else 1.0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "avatarScale"
    )

    val exitButtonOffsetX by animateDpAsState(
        targetValue = if (isConfirming) (-62).dp else 0.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "exitBtnOffset"
    )

    val exitButtonBgColor by animateColorAsState(
        targetValue = if (isConfirming) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "exitBtnBg"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val avatarHovered by avatarInteraction.collectIsHoveredAsState()
            
            val avatarHoverScale by animateFloatAsState(
                targetValue = if (avatarHovered) 1.08f else 1.0f,
                animationSpec = tween(300, easing = com.dexstudios.dex.window.kinematics.DockCardPhysics.HoverEase),
                label = "avatarHoverScale"
            )

            // 34x34dp Profile Avatar Button
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, end = 4.dp)
                    .size(34.dp)
                    .graphicsLayer {
                        scaleX = avatarScale * avatarHoverScale
                        scaleY = avatarScale * avatarHoverScale
                    }
                    .bubbleFluidity()
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = avatarInteraction,
                        indication = null,
                        onClick = onProfileClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.profile_avatar),
                    contentDescription = "Profile Settings",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            val exitInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val exitHovered by exitInteraction.collectIsHoveredAsState()
            
            val exitHoverScale by animateFloatAsState(
                targetValue = if (exitHovered) 1.08f else 1.0f,
                animationSpec = tween(300, easing = com.dexstudios.dex.window.kinematics.DockCardPhysics.HoverEase),
                label = "exitHoverScale"
            )

            // 2-Stage Exit Button Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 1.dp)
                    .height(40.dp)
                    .offset(x = exitButtonOffsetX)
                    .graphicsLayer {
                        scaleX = exitHoverScale
                        scaleY = exitHoverScale
                    }
                    .bubbleFluidity()
                    .clip(RoundedCornerShape(12.dp))
                    .background(exitButtonBgColor)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press) {
                                    isShiftHeld = event.keyboardModifiers.isShiftPressed
                                }
                            }
                        }
                    }
                    .clickable(
                        interactionSource = exitInteraction,
                        indication = null
                    ) {
                        // Check Shift modifier via both PointerEvent and AWT EventQueue
                        val awtShift = try {
                            val currentEvent = java.awt.EventQueue.getCurrentEvent()
                            (currentEvent as? InputEvent)?.isShiftDown == true
                        } catch (_: Exception) {
                            false
                        }

                        if (isShiftHeld || awtShift) {
                            // Instant Exit Bypass (matches WPF: Shift+Click falls through to Invoke-ExitEngine)
                            onExitEngine()
                        } else {
                            if (confirmationStage == ExitConfirmationStage.Idle) {
                                // First click: enter confirmation stage ("Cancel / Shift+Click Exit")
                                confirmationStage = ExitConfirmationStage.Confirming
                            } else {
                                // Second click: CANCEL the exit state, revert to idle.
                                // WPF parity: a regular click never exits — only Shift+Click does.
                                confirmationStage = ExitConfirmationStage.Idle
                            }
                        }
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_fluent_power_settings_new),
                            contentDescription = "Exit Engine",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )

                        Text(
                            text = when {
                                confirmationStage == ExitConfirmationStage.Confirming && (hasActiveTransfers || isMirroringActive) ->
                                    "Transfer Active! Click to Force Exit"
                                confirmationStage == ExitConfirmationStage.Confirming ->
                                    "Cancel / Shift+Click Exit"
                                else -> "Exit Engine"
                            },
                            fontSize = 15.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (confirmationStage == ExitConfirmationStage.Idle) {
                        Text(
                            text = "⌘Q",
                            fontSize = 14.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
