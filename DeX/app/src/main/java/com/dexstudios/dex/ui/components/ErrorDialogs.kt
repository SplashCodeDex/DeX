package com.dexstudios.dex.ui.components

import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.R
import com.dexstudios.dex.ui.components.DeXPanel
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay

@Composable
fun NetworkErrorDialog(
    error: String,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.error_network_title)
) {
    // We use a Dialog with usePlatformDefaultWidth = false to overlay the whole screen,
    // but note: on Android, Dialog creates a new window which cannot sample the backdrop of the main window.
    // To achieve true spatial glass, we must render this as an in-layout overlay.
    // We use a full-screen Box that consumes clicks.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)) // dim layer
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        DeXPanel(
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

@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // State to trigger re-checks when returning to app
    var refreshTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Re-check permissions on resume/launch
    val nearbyGranted = remember(refreshTrigger) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.NEARBY_WIFI_DEVICES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    val notificationsGranted = remember(refreshTrigger) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    val folderGranted = remember(refreshTrigger) {
        com.dexstudios.dex.network.SafStorage.getDownloadsDexUri(context) != null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        DeXPanel(
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.9f)
                .bubbleFluidity(targetScale = 0.98f)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                    }.using(SizeTransform(clip = false))
                },
                label = "onboarding_steps"
            ) { step ->
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (step) {
                        0 -> OnboardingWelcome { currentStep++ }
                        1 -> OnboardingConnectivity(nearbyGranted) {
                            if (nearbyGranted) currentStep++
                            else com.dexstudios.dex.network.PermissionManager.triggerNearby()
                        }
                        2 -> OnboardingNotifications(notificationsGranted) {
                            if (notificationsGranted) currentStep++
                            else com.dexstudios.dex.network.PermissionManager.triggerNotifications()
                        }
                        3 -> OnboardingStorage(folderGranted) {
                            if (folderGranted) currentStep++
                            else com.dexstudios.dex.network.PermissionManager.triggerFolder()
                        }
                        4 -> OnboardingCompletion(onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcome(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(R.drawable.ic_stat_dex),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_step_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_step_welcome_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        DeXButton(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.onboarding_next), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OnboardingConnectivity(isGranted: Boolean, onAction: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OnboardingStepIcon(
            icon = com.dexstudios.dex.ui.icons.MaterialSymbols.Wifi,
            isGranted = isGranted
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_step_connectivity_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_step_connectivity_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        DeXButton(onClick = onAction, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(
                if (isGranted) stringResource(R.string.onboarding_next)
                else stringResource(R.string.onboarding_grant),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OnboardingNotifications(isGranted: Boolean, onAction: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OnboardingStepIcon(
            icon = com.dexstudios.dex.ui.icons.MaterialSymbols.Notifications,
            isGranted = isGranted
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_step_notifications_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_step_notifications_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        DeXButton(onClick = onAction, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(
                if (isGranted) stringResource(R.string.onboarding_next)
                else stringResource(R.string.onboarding_grant),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OnboardingStorage(isGranted: Boolean, onAction: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OnboardingStepIcon(
            icon = ImageVector.vectorResource(R.drawable.ic_folder),
            isGranted = isGranted
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_step_storage_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_step_storage_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        DeXButton(onClick = onAction, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(
                if (isGranted) stringResource(R.string.onboarding_next)
                else stringResource(R.string.onboarding_select_folder),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OnboardingCompletion(onFinish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = com.dexstudios.dex.ui.icons.MaterialSymbols.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF4CAF50)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "All Set!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "You are ready to use DeX. Enjoy seamless file sharing!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        DeXButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.onboarding_done), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OnboardingStepIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, isGranted: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(96.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(24.dp),
                tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
            )
        }
        if (isGranted) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF4CAF50),
                modifier = Modifier.size(32.dp).align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-8).dp)
            ) {
                Icon(
                    imageVector = com.dexstudios.dex.ui.icons.MaterialSymbols.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun PairingRequestDialog(
    alias: String,
    expectedPin: String,
    onAccept: (String) -> Unit,
    onReject: () -> Unit,
    backdrop: Backdrop? = null,
    deadlineElapsedMs: Long = 0L,
) {
    var enteredPin by rememberSaveable { mutableStateOf("") }
    var isError by remember { mutableStateOf(value = false) }
    var visible by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(60) }

    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        visible = true
    }

    // Countdown mirroring the PC's PIN panel expiry.
    LaunchedEffect(Unit) {
        val deadline = if (deadlineElapsedMs > 0) deadlineElapsedMs
                       else SystemClock.elapsedRealtime() + 60_000L
        while (true) {
            val remainingMs = deadline - SystemClock.elapsedRealtime()
            secondsLeft = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
            if (remainingMs <= 0) break
            delay(1000)
        }
        onReject()
    }

    // With a backdrop the dim lives INSIDE the captured layer.
    val overlayDim = if (backdrop == null) Color.Black.copy(alpha = 0.4f) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayDim)
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + scaleIn(tween(400, easing = BackOut), initialScale = 0.8f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.8f)
        ) {
            val cardContent: @Composable BoxScope.() -> Unit = {
                // Close Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .bubbleFluidity(targetScale = 0.9f)
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
                        imageVector = MaterialSymbols.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(
                    modifier = Modifier.padding(top = 48.dp, bottom = 24.dp, start = 32.dp, end = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.pairing_enter_pin_on_device, alias),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    PinInputField(
                        value = enteredPin,
                        onValueChange = {
                            if (it.length <= 5) {
                                if (it.length > enteredPin.length) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                enteredPin = it
                                isError = false
                            }
                        },
                        isError = isError
                    )

                    if (isError) {
                        Text(
                            stringResource(R.string.pairing_wrong_pin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    Text(
                        stringResource(R.string.pairing_expires_in, secondsLeft),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (secondsLeft <= 10) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    val confirmInteractionSource = remember { MutableInteractionSource() }
                    val isConfirmPressed by confirmInteractionSource.collectIsPressedAsState()
                    val confirmPressProgress by animateFloatAsState(
                        targetValue = if (isConfirmPressed) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f)
                    )

                    val isComplete = enteredPin.length == 5

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .graphicsLayer {
                                val s = 1f - 0.02f * confirmPressProgress
                                scaleX = s
                                scaleY = s
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (backdrop != null) {
                            LiquidGlassPanel(
                                backdrop = backdrop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        enabled = isComplete,
                                        interactionSource = confirmInteractionSource,
                                        indication = null,
                                        onClick = {
                                            if (enteredPin == expectedPin) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onAccept(enteredPin)
                                            } else {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                isError = true
                                            }
                                        }
                                    ),
                                config = LiquidGlassPresets.FlatInteractive.copy(
                                    surfaceTintAlpha = if (isComplete) 0.25f else 0.1f
                                ),
                                content = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ConfirmButtonContent(isComplete)
                                    }
                                }
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        enabled = isComplete,
                                        interactionSource = confirmInteractionSource,
                                        indication = null,
                                        onClick = {
                                            if (enteredPin == expectedPin) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onAccept(enteredPin)
                                            } else {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                isError = true
                                            }
                                        }
                                    ),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isComplete) 0.5f else 0.2f),
                                border = if (isComplete) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                content = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ConfirmButtonContent(isComplete)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (backdrop != null) {
                LiquidGlassPanel(
                    backdrop = backdrop,
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(0.9f)
                        .bubbleFluidity(targetScale = 0.99f)
                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), LiquidGlassPresets.Flat.shape),
                    shape = LiquidGlassPresets.Flat.shape,
                    config = LiquidGlassPresets.Flat,
                    content = cardContent
                )
            } else {
                DeXPanel(
                    shape = RoundedCornerShape(48.dp),
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(0.9f)
                        .bubbleFluidity(targetScale = 0.99f),
                    content = cardContent
                )
            }
        }
    }
}

@Composable
private fun ConfirmButtonContent(isComplete: Boolean) {
    Crossfade(targetState = isComplete, animationSpec = tween(300)) { complete ->
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

@Composable
fun AnimatedWaitingDots() {
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .size(8.dp) // Big (not too big)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(Color.Black, CircleShape)
            )
        }
    }
}

val BackOut = Easing { fraction ->
    val s = 1.70158f
    val t = fraction - 1.0f
    t * t * ((s + 1.0f) * t + s) + 1.0f
}

@Composable
fun PinInputField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
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
        )
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
        modifier = Modifier.shake(isError),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                // Real text field capturing input but visually hidden
                Box(Modifier.fillMaxWidth()) { innerTextField() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(5) { index ->
                        val char = when {
                            index < value.length -> value[index].toString()
                            else -> ""
                        }
                        val isFocused = index == value.length
                        val isFilled = index < value.length

                        val scale by animateFloatAsState(
                            targetValue = if (isFilled) 1.05f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .background(
                                    color = when {
                                        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                        isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = when {
                                        isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        isFilled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            )

                            // Animated Blinking Cursor Caret
                            if (isFocused) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 14.dp)
                                        .width(16.dp)
                                        .height(3.dp)
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

fun Modifier.shake(enabled: Boolean): Modifier = composed {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            offset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 300
                    -10f at 50
                    10f at 100
                    -10f at 150
                    10f at 200
                    -5f at 250
                    0f at 300
                }
            )
        }
    }
    this.offset { IntOffset(offset.value.dp.roundToPx(), 0) }
}
