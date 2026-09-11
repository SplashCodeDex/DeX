package com.dexstudios.dex.window.components
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.DefaultGlareIntensity
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.components.island.DynamicContentBlurConfig
import com.dexstudios.dex.core.designsystem.components.island.DynamicFluidityConfig
import com.dexstudios.dex.core.designsystem.components.island.DynamicMotionConfig
import com.dexstudios.dex.core.designsystem.components.island.transientContentBlur
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_power_filled
import com.dexstudios.dex.core.designsystem.generated.resources.profile_avatar
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.mirror.toImageBitmap
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import java.awt.Toolkit
import java.awt.event.InputEvent

enum class ExitConfirmationStage {
    Idle,
    Confirming,
}

/**
 * BottomDockPanel:
 * - Dedicated Profile Avatar Button (34dp circular, opens Settings) with live dynamic press optics
 * - 1dp horizontal accent divider
 * - 2-stage Exit Engine confirmation (Shift+Click bypass, active transfer force-exit, -58dp expansion, 3s auto-revert timer)
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
    deviceConfig: DeviceConfig = org.koin.compose.koinInject(),
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
    val motionConfig = DynamicMotionConfig.Default

    val googleProfile by deviceConfig.googleProfileFlow.collectAsState()
    val deviceAlias by deviceConfig.aliasFlow.collectAsState()

    val avatarBitmap by produceState<ImageBitmap?>(initialValue = null, googleProfile.picture) {
        val url = googleProfile.picture
        if (url.isBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                java.net.URI(url).toURL().readBytes().toImageBitmap()
            }.getOrNull()
        }
    }

    val avatarInteraction = remember { MutableInteractionSource() }
    val avatarHovered by avatarInteraction.collectIsHoveredAsState()
    var isAvatarFluidityPressed by remember { mutableStateOf(false) }
    val isAvatarPressedRaw by avatarInteraction.collectIsPressedAsState()
    val isAvatarPressed = isAvatarPressedRaw || isAvatarFluidityPressed
    val avatarPressProgress by animateFloatAsState(
        targetValue = if (isAvatarPressed) 1f else 0f,
        animationSpec = motionConfig.springSpec(isAvatarPressed),
        label = "avatarPressProgress",
    )

    val avatarHoverScale by animateFloatAsState(
        targetValue = if (avatarHovered && !isConfirming) 1.08f else 1.0f,
        animationSpec = DockCardAnimations.HoverSpec,
        label = "avatarHoverScale",
    )

    val avatarScale by animateFloatAsState(
        targetValue = if (isConfirming) 0.6f else 1.0f,
        animationSpec = motionConfig.springSpec(isConfirming),
        label = "avatarScale",
    )
    val avatarShadowElevation = (4.dp * (1f - 0.20f * avatarPressProgress)).coerceAtLeast(0.dp)

    val exitExpandAmount by animateDpAsState(
        targetValue = if (isConfirming) 58.dp else 0.dp,
        animationSpec = motionConfig.springSpec(isConfirming),
        label = "exitExpandAmount",
    )

    val exitHeight by animateDpAsState(
        targetValue = if (isConfirming) 52.dp else 48.dp,
        animationSpec = motionConfig.springSpec(isConfirming),
        label = "exitHeight",
    )

    val exitInteraction = remember { MutableInteractionSource() }
    val exitHovered by exitInteraction.collectIsHoveredAsState()
    var isExitFluidityPressed by remember { mutableStateOf(false) }
    val isExitPressedRaw by exitInteraction.collectIsPressedAsState()
    val isExitPressed = isExitPressedRaw || isExitFluidityPressed
    val exitPressProgress by animateFloatAsState(
        targetValue = if (isExitPressed) 1f else 0f,
        animationSpec = motionConfig.springSpec(isExitPressed),
        label = "exitPressProgress",
    )

    val exitButtonBgColor by animateColorAsState(
        targetValue = when {
            isConfirming || exitHovered -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        },
        animationSpec = motionConfig.springSpec(isConfirming),
        label = "exitBtnBg",
    )
    val exitCenterBias by animateFloatAsState(
        targetValue = if (isConfirming) 1f else 0f,
        animationSpec = motionConfig.springSpec(isConfirming),
        label = "exitCenterBias",
    )

    val exitHoverScale by animateFloatAsState(
        targetValue = if (exitHovered && !isConfirming) 1.08f else 1.0f,
        animationSpec = DockCardAnimations.HoverSpec,
        label = "exitHoverScale",
    )

    val exitShadowElevationBase by animateDpAsState(
        targetValue = if (isConfirming) 8.dp else 4.dp,
        animationSpec = motionConfig.springSpec(isConfirming),
        label = "exitShadowElevation",
    )
    val exitShadowElevation = (exitShadowElevationBase * (1f - 0.20f * exitPressProgress)).coerceAtLeast(0.dp)

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
            // 34x34dp Profile Avatar Button (Non-expanding circular button, opens Settings)
            Box(
                modifier = Modifier
                    .zIndex(if (avatarHovered) 1f else 0f)
                    .padding(start = 16.dp, end = 8.dp)
                    .size(34.dp)
                    .graphicsLayer {
                        scaleX = avatarScale * avatarHoverScale
                        scaleY = avatarScale * avatarHoverScale
                    }
                    .bubbleFluidity(
                        config = DynamicFluidityConfig.Default,
                        onPressedChanged = { isAvatarFluidityPressed = it },
                    )
                    .shadow(
                        elevation = avatarShadowElevation,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.20f),
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                    )
                    .clip(CircleShape)
                    .shinyGlare(
                        shape = CircleShape,
                        intensity = DefaultGlareIntensity * (1f + 0.60f * avatarPressProgress),
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = avatarInteraction,
                        indication = null,
                        onClick = onProfileClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = avatarBitmap
                when {
                    avatar != null -> {
                        Image(
                            bitmap = avatar,
                            contentDescription = "Profile Settings",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    }

                    googleProfile.email.isNotBlank() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            val initial = googleProfile.name.ifBlank { googleProfile.email }.firstOrNull()?.uppercase()
                            if (initial != null) {
                                Text(
                                    text = initial,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }

                    else -> {
                        Image(
                            painter = painterResource(Res.drawable.profile_avatar),
                            contentDescription = "Profile Settings",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    }
                }
            }

            // 2-Stage Exit Button Container (Strict Stadium Capsule CircleShape)
            Box(
                modifier = Modifier
                    .zIndex(if (exitHovered || isConfirming) 2f else 0f)
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
                    .bubbleFluidity(
                        config = DynamicFluidityConfig.Default,
                        onPressedChanged = { isExitFluidityPressed = it },
                    )
                    .shadow(
                        elevation = exitShadowElevation,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = if (isConfirming) 0.30f else 0.20f),
                        ambientColor = Color.Black.copy(alpha = if (isConfirming) 0.15f else 0.10f),
                    )
                    .clip(CircleShape)
                    .background(exitButtonBgColor)
                    .shinyGlare(
                        shape = CircleShape,
                        intensity = DefaultGlareIntensity * (1f + 0.60f * exitPressProgress),
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
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
                                        // The label promised "Click to Force Exit" - honor it.
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
                                val enterSpec = spring<Float>(dampingRatio = 0.70f, stiffness = 500f)
                                val exitSpec = spring<Float>(dampingRatio = 0.70f, stiffness = 500f)
                                (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = enterSpec))
                                    .togetherWith(fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.88f, animationSpec = exitSpec))
                            },
                            modifier = Modifier.transientContentBlur(
                                trigger = confirmationStage,
                                config = DynamicContentBlurConfig.Default,
                                motion = motionConfig,
                            ),
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
