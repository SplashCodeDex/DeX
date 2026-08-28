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
import androidx.compose.ui.graphics.graphicsLayer
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
fun OnboardingDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val deviceConfig: DeviceConfig = koinInject()
    val googleProfile by deviceConfig.googleProfileFlow.collectAsStateWithLifecycle()

    // State to trigger re-checks when returning to app
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val nearbyPermanentlyDenied by PermissionManager.nearbyPermanentlyDenied.collectAsStateWithLifecycle()
    val notificationsPermanentlyDenied by PermissionManager.notificationsPermanentlyDenied.collectAsStateWithLifecycle()

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

    LaunchedEffect(nearbyGranted) {
        if (nearbyGranted) PermissionManager.setNearbyPermanentlyDenied(false)
    }

    LaunchedEffect(notificationsGranted) {
        if (notificationsGranted) PermissionManager.setNotificationsPermanentlyDenied(false)
    }

    // Determine the steps sequence
    val steps = remember(nearbyGranted, notificationsGranted, googleProfile.email) {
        mutableListOf<Int>().apply {
            add(0) // Welcome
            if (!nearbyGranted) add(1) // Connectivity
            if (!notificationsGranted) add(2) // Notifications
            if (googleProfile.email.isBlank()) add(3) // Identity
            add(4) // Completion
        }.toList()
    }

    var currentStepIdx by remember { mutableIntStateOf(0) }
    val currentStep = steps.getOrElse(currentStepIdx) { 4 }

    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        visible = true
    }

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
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
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
                enter = fadeIn(tween(500)) + scaleIn(tween(500, easing = BackOut), initialScale = 0.9f),
                exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.9f)
            ) {
                DeXGlareCard(
                shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(0.9f)
                        .bubbleFluidity(targetScale = 0.98f, pullFactor = 0.02f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Consume clicks on card
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.weight(1f, fill = false)) {
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
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    when (step) {
                                        0 -> OnboardingWelcome { currentStepIdx++ }
                                        1 -> OnboardingConnectivity(
                                            isGranted = nearbyGranted,
                                            isPermanentlyDenied = nearbyPermanentlyDenied
                                        ) {
                                            if (nearbyGranted) currentStepIdx++
                                            else PermissionManager.triggerNearby()
                                        }
                                        2 -> OnboardingNotifications(
                                            isGranted = notificationsGranted,
                                            isPermanentlyDenied = notificationsPermanentlyDenied
                                        ) {
                                            if (notificationsGranted) currentStepIdx++
                                            else PermissionManager.triggerNotifications()
                                        }
                                        3 -> OnboardingIdentity(googleProfile.email) { currentStepIdx++ }
                                        4 -> OnboardingCompletion {
                                            visible = false
                                            scope.launch {
                                                delay(300.milliseconds)
                                                onDismiss()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (currentStep != 4) { // Don't show dots on completion
                            Spacer(Modifier.height(24.dp))
                            OnboardingProgressDots(
                                count = steps.size,
                                selectedIndex = currentStepIdx
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProgressDots(count: Int, selectedIndex: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val isSelected = index == selectedIndex
            val width by animateDpAsState(
                targetValue = if (isSelected) 16.dp else 6.dp,
                animationSpec = spring(Spring.DampingRatioMediumBouncy),
                label = "dotWidth"
            )
            val color by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                label = "dotColor"
            )

            Box(
                modifier = Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingWelcomePreview() {
    MaterialTheme {
        Box(Modifier.padding(24.dp)) {
            OnboardingWelcome(onNext = {})
        }
    }
}

@Composable
private fun OnboardingWelcome(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.onboarding_step_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))
        Image(
            painter = painterResource(R.drawable.dex_logo),
            contentDescription = null,
            modifier = Modifier.size(72.dp) // Slightly bigger logo as it's the centerpiece
        )
        Spacer(Modifier.height(24.dp))
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
private fun OnboardingConnectivity(
    isGranted: Boolean,
    isPermanentlyDenied: Boolean,
    onAction: () -> Unit
) {
    LaunchedEffect(isGranted) {
        if (isGranted) {
            delay(600.milliseconds)
            onAction()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OnboardingStepIcon(
            icon = DeXIcons.Wifi,
            isGranted = isGranted
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_step_connectivity_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (isPermanentlyDenied && !isGranted) stringResource(R.string.onboarding_step_connectivity_rationale)
            else stringResource(R.string.onboarding_step_connectivity_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        DeXButton(
            onClick = {
                if (isPermanentlyDenied && !isGranted) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                } else {
                    onAction()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                when {
                    isGranted -> stringResource(R.string.onboarding_next)
                    isPermanentlyDenied -> stringResource(R.string.onboarding_open_settings)
                    else -> stringResource(R.string.onboarding_grant)
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OnboardingNotifications(
    isGranted: Boolean,
    isPermanentlyDenied: Boolean,
    onAction: () -> Unit
) {
    LaunchedEffect(isGranted) {
        if (isGranted) {
            delay(600.milliseconds)
            onAction()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OnboardingStepIcon(
            icon = DeXIcons.Notifications,
            isGranted = isGranted
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_step_notifications_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (isPermanentlyDenied && !isGranted) stringResource(R.string.onboarding_step_notifications_rationale)
            else stringResource(R.string.onboarding_step_notifications_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        DeXButton(
            onClick = {
                if (isPermanentlyDenied && !isGranted) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                } else {
                    onAction()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                when {
                    isGranted -> stringResource(R.string.onboarding_next)
                    isPermanentlyDenied -> stringResource(R.string.onboarding_open_settings)
                    else -> stringResource(R.string.onboarding_grant)
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OnboardingIdentity(
    email: String,
    onNext: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceConfig: DeviceConfig = koinInject()
    val resources = androidx.compose.ui.platform.LocalResources.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OnboardingStepIcon(
            icon = DeXIcons.AccountCircle,
            isGranted = email.isNotBlank()
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_step_identity_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_step_identity_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        if (email.isBlank()) {
            DeXButton(
                onClick = {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        scope.launch {
                            val credential = GoogleSignInManager.signIn(activity)
                            val result = credential?.let { GoogleSignInManager.applyToDeviceConfig(it, deviceConfig) }
                            if (result != null) {
                                android.widget.Toast.makeText(context, resources.getString(R.string.google_signed_in_as, result), android.widget.Toast.LENGTH_LONG).show()
                                onNext()
                            } else {
                                android.widget.Toast.makeText(context, resources.getString(R.string.google_sign_in_failed), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = DeXIcons.Google,
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(stringResource(R.string.google_sign_in), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            DeXTextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_skip), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        } else {
            DeXButton(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.onboarding_next), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OnboardingCompletion(onFinish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = DeXIcons.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "All Set!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
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
private fun OnboardingStepIcon(icon: ImageVector, isGranted: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            modifier = Modifier.size(96.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(24.dp),
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        if (isGranted) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp).align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-8).dp)
                    ) {
                        Icon(
                            imageVector = DeXIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(6.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
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
            repeat(5) {
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
                                        if (it.length <= 5) {
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

                        val isComplete = enteredPin.length == 5

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
                    repeat(5) { index ->
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


