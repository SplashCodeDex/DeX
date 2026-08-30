package com.dexstudios.dex.ui.components

import android.os.SystemClock
import com.dexstudios.dex.ui.icons.MaterialSymbols as DeXIcons
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.network.GoogleSignInManager
import com.dexstudios.dex.network.PermissionManager
import com.dexstudios.dex.network.ProtocolKeys
import com.dexstudios.dex.ui.components.DeXPanel
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun NetworkErrorDialog(
    error: String,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.error_network_title),
    modifier: Modifier = Modifier
) {
    val dialogBackdrop = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        // 1. Capture the Dim Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(dialogBackdrop)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)) // dim layer
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // 2. Dialog sits ON TOP as a sibling, not being captured. Safe.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            DeXGlareCard(
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(0.9f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // consume clicks on dialog
                    )
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(error, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        DeXTextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.dismiss), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PairingRequestDialog(
    alias: String,
    expectedPin: String,
    onAccept: (String) -> Unit,
    onFinished: () -> Unit = {},
    onReject: () -> Unit,
    deadlineElapsedMs: Long = 0L,
    onDigitEntered: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var enteredPin by rememberSaveable { mutableStateOf("") }
    var isError by remember { mutableStateOf(value = false) }
    var isSuccess by remember { mutableStateOf(value = false) }
    var greenSlotsCount by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(60) }
    var progress by remember { mutableFloatStateOf(1f) }

    val haptics = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            // Transmit pair-response immediately so the PC verifies the PIN proof without animation latency
            onAccept(enteredPin)
            // Domino effect: 80ms per slot
            repeat(ProtocolKeys.PIN_LENGTH) {
                delay(80.milliseconds)
                greenSlotsCount++
            }
            delay(200.milliseconds) // pause for merging
            // The morph happens via AnimatedContent triggered by isSuccess later
            delay(1000.milliseconds) // Allow full animation to play before dismissal
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        visible = true
        delay(300.milliseconds)
        focusRequester.requestFocus()
    }

    // Countdown mirroring the PC's PIN panel expiry.
    LaunchedEffect(Unit) {
        val durationMs = 60_000L
        val deadline = if (deadlineElapsedMs > 0) deadlineElapsedMs
                       else SystemClock.elapsedRealtime() + durationMs

        while (true) {
            val remainingMs = deadline - SystemClock.elapsedRealtime()
            secondsLeft = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
            progress = (remainingMs.toFloat() / durationMs).coerceIn(0f, 1f)

            if (remainingMs <= 0) break
            delay(100.milliseconds) // Smoother progress updates
        }
        onReject()
    }

    val dimAlpha by animateFloatAsState(
        targetValue = if (isSuccess) 0.2f else 0.6f,
        animationSpec = tween(800)
    )

    val dialogBackdrop = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        // 1. Capture the Dim Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(dialogBackdrop)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = dimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            )
        }

        // 2. Dialog sits ON TOP as a sibling, not being captured. Safe.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + scaleIn(tween(400, easing = BackOut), initialScale = 0.85f),
                exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.85f)
            ) {
                DeXGlareCard(
                shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(0.9f)
                ) {
                    // Close Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                visible = false
                                onReject()
                            }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = DeXIcons.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.padding(top = 24.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = DeXIcons.Devices,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            stringResource(R.string.pairing_enter_pin_on_device, alias),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        AnimatedContent(
                            targetState = isSuccess,
                            transitionSpec = {
                                if (targetState) {
                                    (scaleIn(tween(500, easing = BackOut), initialScale = 0.5f) + fadeIn(tween(500)))
                                        .togetherWith(fadeOut(tween(300)))
                                } else {
                                    fadeIn().togetherWith(fadeOut())
                                }
                            },
                            label = "success_morph"
                        ) { success ->
                            if (success) {
                                Icon(
                                    imageVector = DeXIcons.CheckCircle,
                                    contentDescription = "Success",
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                PinInputField(
                                    value = enteredPin,
                                    onValueChange = {
                                        if (it.length <= ProtocolKeys.PIN_LENGTH) {
                                            if (it.length > enteredPin.length) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            enteredPin = it
                                            isError = false
                                            onDigitEntered(it.length)
                                        }
                                    },
                                    isError = isError,
                                    isSuccess = isSuccess,
                                    greenSlotsCount = greenSlotsCount,
                                    focusRequester = focusRequester
                                )
                            }
                        }

                        if (isError && !isSuccess) {
                            Text(
                                stringResource(R.string.pairing_wrong_pin),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Timer Section
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = if (secondsLeft <= 10) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.pairing_expires_in, secondsLeft),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (secondsLeft <= 10) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        val isComplete = enteredPin.length == ProtocolKeys.PIN_LENGTH

                        DeXButton(
                            onClick = {
                                if (enteredPin == expectedPin) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isSuccess = true
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isError = true
                                    enteredPin = ""
                                    onDigitEntered(-1)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = isComplete && !isSuccess,
                            colors = if (isSuccess) ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) else ButtonDefaults.buttonColors()
                        ) {
                            ConfirmButtonContent(isComplete, isSuccess)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmButtonContent(isComplete: Boolean, isSuccess: Boolean = false) {
    AnimatedContent(
        targetState = isSuccess,
        transitionSpec = {
            (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
        },
        label = "button_text_morph"
    ) { success ->
        if (success) {
            Text(
                "Connected",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Crossfade(targetState = isComplete, animationSpec = tween(300), label = "confirm_crossfade") { complete ->
                if (complete) {
                    Text(
                        "confirm",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        "enter pin",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

val BackOut = Easing { fraction ->
    val s = 1.70158f
    val t = fraction - 1.0f
    (t * t * ((s + 1.0f) * t + s) + 1.0f)
}

@Composable
fun PinInputField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    isSuccess: Boolean = false,
    greenSlotsCount: Int = 0,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val infiniteTransition = rememberInfiniteTransition()
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            }
        ),
        label = "cursor_blink"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
        modifier = Modifier
            .focusRequester(focusRequester)
            .shake(isError),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                // Real text field capturing input but visually hidden
                Box(Modifier.fillMaxWidth()) { innerTextField() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(ProtocolKeys.PIN_LENGTH) { index ->
                        val char = when {
                            index < value.length -> value[index].toString()
                            else -> ""
                        }
                        val isFocused = index == value.length
                        val isFilled = index < value.length
                        val isGreen = index < greenSlotsCount

                        val slotOffset by animateDpAsState(
                            targetValue = if (isSuccess) {
                                // Merge towards center: index 2 is center
                                (2 - index).dp * 48 // Approximate width + spacing
                            } else 0.dp,
                            animationSpec = tween(600, easing = FastOutSlowInEasing),
                            label = "merge_offset"
                        )

                        val slotAlpha by animateFloatAsState(
                            targetValue = if (isSuccess) 0f else 1f,
                            animationSpec = tween(400, delayMillis = 200),
                            label = "merge_alpha"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .graphicsLayer {
                                    translationX = slotOffset.toPx()
                                    alpha = slotAlpha
                                }
                                .background(
                                    color = when {
                                        isGreen -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                        isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = if (isFocused || isGreen) 2.dp else 1.dp,
                                    color = when {
                                        isGreen -> MaterialTheme.colorScheme.primary
                                        isError -> MaterialTheme.colorScheme.error
                                        isFocused -> MaterialTheme.colorScheme.primary
                                        isFilled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = char,
                                transitionSpec = {
                                    (scaleIn(animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) + fadeIn())
                                        .togetherWith(fadeOut(animationSpec = tween(100)))
                                },
                                label = "digit_animation"
                            ) { digit ->
                                Text(
                                    text = digit,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            // Animated Blinking Cursor Caret
                            if (isFocused && !isSuccess) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 12.dp)
                                        .width(12.dp)
                                        .height(2.dp)
                                        .clip(CircleShape)
                                        .graphicsLayer { alpha = cursorAlpha }
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

fun Modifier.shake(enabled: Boolean): Modifier = this then ShakeElement(enabled)

private data class ShakeElement(val enabled: Boolean) : ModifierNodeElement<ShakeNode>() {
    override fun create(): ShakeNode = ShakeNode(enabled)
    override fun update(node: ShakeNode) {
        if (enabled && !node.enabled) {
            node.shake()
        }
        node.enabled = enabled
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shake"
        properties["enabled"] = enabled
    }
}

private class ShakeNode(var enabled: Boolean) : Modifier.Node(), LayoutModifierNode {
    private val offset = Animatable(0f)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationX = offset.value.dp.toPx()
            }
        }
    }

    override fun onAttach() {
        if (enabled) {
            shake()
        }
    }

    fun shake() {
        coroutineScope.launch {
            offset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 300
                    10f at 50
                    (-10f) at 100
                    10f at 150
                    (-10f) at 200
                    5f at 250
                    0f at 300
                }
            )
        }
    }
}


