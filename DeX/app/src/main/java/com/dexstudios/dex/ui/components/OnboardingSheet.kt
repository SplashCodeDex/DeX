package com.dexstudios.dex.ui.components

import com.dexstudios.dex.ui.icons.MaterialSymbols as DeXIcons
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.network.GoogleSignInManager
import com.dexstudios.dex.network.PermissionManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * Sheet-based onboarding flow hosted inside the 3-tier [NavBottomSheet] engine.
 * The sheet is locked (no drag, no back, no outside-tap dismiss) and only the
 * final completion button finishes the flow.
 */
@Composable
fun OnboardingSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onboardingBackdrop = rememberLayerBackdrop()

    NavBottomSheet(
        backdrop = onboardingBackdrop,
        modifier = modifier,
        initialTier = SheetTier.Half,
        dragEnabled = false,
        onDismiss = {},
        sheetContent = { _, _, _, expandTo, _ ->
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

            // Tier mapping: Welcome -> 50%, permission/identity steps -> 80%, Completion -> 100%
            LaunchedEffect(currentStepIdx) {
                when (currentStep) {
                    0 -> expandTo(SheetTier.Half)
                    4 -> expandTo(SheetTier.Full)
                    else -> expandTo(SheetTier.High)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Intrinsic-height scrolling area so the CTA is never clipped when content exceeds the tier height
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                    // Persist completion and dismiss (sheet is locked, only this button finishes)
                                    context.getSharedPreferences("dex_onboarding", android.content.Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("onboarding_done", true)
                                        .apply()
                                    onDismiss()
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
                    Spacer(Modifier.height(24.dp))
                }
            }
        },
        content = { _, _ ->
            // Dim backdrop only (no logo screen) — matches the original onboarding dialog's dimmed presentation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(onboardingBackdrop)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            )
        }
    )
}

@Composable
internal fun OnboardingProgressDots(count: Int, selectedIndex: Int) {
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
internal fun OnboardingWelcome(onNext: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/DevicesMorph.json"))

    // Scrollable so the full welcome content (incl. the CTA) is reachable at the 50% tier on any screen
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        // Full-duration looping devices morph (DevicesMorph is 456 frames @ 30fps = ~15.2s; Lottie's
        // default speed assumes 60fps and would truncate it, so halve speed to play the whole clip)
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            speed = 0.5f,
            modifier = Modifier.size(140.dp)
        )
        Spacer(Modifier.height(16.dp))
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
            Text(stringResource(R.string.onboarding_get_started), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun OnboardingConnectivity(
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
internal fun OnboardingNotifications(
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
internal fun OnboardingIdentity(
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
internal fun OnboardingCompletion(onFinish: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/device_connected.json"))

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // One-shot connected animation instead of the static check-circle icon
        LottieAnimation(
            composition = composition,
            iterations = 1,
            modifier = Modifier.size(140.dp)
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
internal fun OnboardingStepIcon(icon: ImageVector, isGranted: Boolean) {
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
