package com.dexstudios.dex.window.components
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_power_filled
import com.dexstudios.dex.core.designsystem.generated.resources.profile_avatar
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import java.awt.Toolkit
import java.awt.event.InputEvent

enum class ExitConfirmationStage {
    Idle,
    Confirming,
}

/**
 * BottomDockPanel:
 * - 34x34dp circular profile avatar button (opens Settings, scales to 0.6x during confirmation)
 * - 1dp horizontal accent divider
 * - 2-stage Exit Engine confirmation (Shift+Click bypass, active transfer force-exit, -62dp expansion, 3s auto-revert timer)
 */
@Composable
fun BottomDockPanel(
    onProfileClick: () -> Unit,
    onExitEngine: () -> Unit,
    hasActiveTransfers: Boolean = false,
    isMirroringActive: Boolean = false,
    // Tracks the dock card's real visibility. The panel stays composed while the card
    // is hidden (alpha-painted), so time-based affordances (Shift poll) need this to
    // switch themselves off instead of idling forever behind an invisible surface.
    isPanelVisible: Boolean = true,
    modifier: Modifier = Modifier,
    // Live engines for the click-time re-check below; the rendered props are only a
    // paint-time snapshot and an upload can settle between render and click.
    clientEngine: com.dexstudios.dex.core.network.ClientEngine = org.koin.compose.koinInject(),
    fileSender: com.dexstudios.dex.desktop.transfer.DesktopFileSendService = org.koin.compose.koinInject(),
) {
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
        animationSpec = DockCardAnimations.SmoothEase,
        label = "avatarScale",
    )

    val exitExpandAmount by animateDpAsState(
        targetValue = if (isConfirming) 58.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "exitExpandAmount",
    )

    val exitHeight by animateDpAsState(
        targetValue = if (isConfirming) 41.dp else 40.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "exitHeight",
    )

    val exitInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val exitHovered by exitInteraction.collectIsHoveredAsState()

    val exitButtonBgColor by animateColorAsState(
        targetValue = when {
            isConfirming || exitHovered -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        },
        animationSpec = DockCardAnimations.LinearColorSpec,
        label = "exitBtnBg",
    )
    val exitCenterBias by animateFloatAsState(
        targetValue = if (isConfirming) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "exitCenterBias",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatarInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val avatarHovered by avatarInteraction.collectIsHoveredAsState()

            val avatarHoverScale by animateFloatAsState(
                targetValue = if (avatarHovered) 1.08f else 1.0f,
                animationSpec = DockCardAnimations.HoverSpec,
                label = "avatarHoverScale",
            )

            // 34x34dp Profile Avatar Button
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, end = 8.dp)
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
                        onClick = onProfileClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.profile_avatar),
                    contentDescription = "Profile Settings",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            val exitHoverScale by animateFloatAsState(
                targetValue = if (exitHovered && !isConfirming) 1.08f else 1.0f,
                animationSpec = DockCardAnimations.HoverSpec,
                label = "exitHoverScale",
            )

            // 2-Stage Exit Button Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
                    .height(exitHeight)
                    .layout { measurable, constraints ->
                        val extra = exitExpandAmount.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(
                                maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else androidx.compose.ui.unit.Constraints.Infinity,
                                minWidth = constraints.minWidth + extra,
                            ),
                        )
                        layout(placeable.width - extra, placeable.height) {
                            placeable.place(-extra, 0)
                        }
                    }
                    .graphicsLayer {
                        scaleX = exitHoverScale
                        scaleY = exitHoverScale
                    }
                    .bubbleFluidity(targetScale = 0.95f, pullFactor = 0.05f)
                    .clip(RoundedCornerShape(30.dp))
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
                        indication = null,
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
                            when (confirmationStage) {
                                ExitConfirmationStage.Idle ->
                                    // First click: enter confirmation stage ("Cancel / Shift+Click Exit")
                                    confirmationStage = ExitConfirmationStage.Confirming

                                ExitConfirmationStage.Confirming -> {
                                    // Live re-check at click time: trusting the rendered
                                    // props alone could turn a cancel into an exit (transfer
                                    // settled since paint) or miss a just-started transfer.
                                    val transferLiveNow =
                                        clientEngine.uploadState.value.isUploading || fileSender.isSessionActive()
                                    if (isMirroringActive || transferLiveNow) {
                                        // The label promised "Click to Force Exit" — honor it.
                                        // A plain click while a transfer/mirror is live force-exits;
                                        // without active work a plain click only cancels (WPF parity).
                                        onExitEngine()
                                    } else {
                                        confirmationStage = ExitConfirmationStage.Idle
                                    }
                                }
                            }
                        }
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left spacer pushes text to center when expanded, relative to the available space
                    val bias = exitCenterBias.coerceIn(0f, 1f)
                    if (bias > 0.001f) {
                        Spacer(modifier = Modifier.weight(bias))
                    }

                    // Main Content (Icon + Text)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_fluent_power_filled),
                            contentDescription = "Exit Engine",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )

                        AnimatedContent(
                            targetState = confirmationStage,
                            transitionSpec = {
                                fadeIn(DockCardAnimations.LinearFadeSpec) togetherWith fadeOut(DockCardAnimations.LinearFadeSpec)
                            },
                            label = "exitText",
                        ) { state ->
                            Text(
                                text = when {
                                    state == ExitConfirmationStage.Confirming && (hasActiveTransfers || isMirroringActive) ->
                                        "Transfer Active! Click to Force Exit"

                                    state == ExitConfirmationStage.Confirming ->
                                        "Cancel / Shift+Click Exit"

                                    else -> "Exit Engine"
                                },
                                fontSize = 15.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Right spacer balances the left spacer when expanded
                    Spacer(modifier = Modifier.weight(1f))

                    // Live Shift+Click affordance pinned to the far right. Hidden only in
                    // the "Transfer Active! Click to Force Exit" stage, where a plain click
                    // already exits and a Shift hint would contradict the label.
                    if (confirmationStage == ExitConfirmationStage.Idle || !(hasActiveTransfers || isMirroringActive)) {
                        ShiftClickCombo(modifier = Modifier.padding(start = 6.dp), isPanelVisible = isPanelVisible)
                    }
                }
            }
        }
    }
}
