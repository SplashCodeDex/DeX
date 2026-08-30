package com.dexstudios.dex.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dexstudios.dex.network.AuthState
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.network.DownloadState
import com.dexstudios.dex.network.UploadState
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.Backdrop
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// ============================================================================
// Desktop DevicesMorph exact playback constants
// ============================================================================
private const val TOTAL_FRAMES = 456f // animation "op"
private const val SETTLE_FRAME = 455f // last frame with the fully settled DeX
private const val SETTLE_PROGRESS = SETTLE_FRAME / TOTAL_FRAMES
private const val RAMP_START_FRAME = 415f // frame where the slow-down begins
private const val MIN_SPEED = 0.12f // final playback speed (gentle drift to a stop)
private const val ANIMATION_FPS = 60f
private const val HOLD_ON_DEX_MS = 4_000L // hold on DeX before the transition
private const val FADE_OUT_MS = 600 // DeX fades out completely (to blank)
private const val FADE_IN_MS = 600 // monitor (start frame) fades in

@Composable
fun DeviceCarousel(
    devices: List<DiscoveredDevice>,
    selectedDevice: DiscoveredDevice?,
    backdrop: Backdrop?,
    onDeviceSelect: (DiscoveredDevice) -> Unit,
    onDeviceLongClick: (DiscoveredDevice) -> Unit,
    onAddDeviceClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    uploadState: UploadState = UploadState(),
    downloadState: DownloadState = DownloadState(),
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (devices.isEmpty()) {
            EmptyDiscoveryCarousel(backdrop = backdrop)
        } else {
            ConnectedDevicesCarousel(
                devices = devices,
                selectedDevice = selectedDevice,
                backdrop = backdrop,
                onDeviceSelect = onDeviceSelect,
                onDeviceLongClick = onDeviceLongClick,
                onAddDeviceClick = onAddDeviceClick,
                uploadState = uploadState,
                downloadState = downloadState,
            )
        }
    }
}

/**
 * Animated Empty State: Displays DevicesMorph.json Lottie with exact desktop playback tuning,
 * bigger scale, and centered in the sheet.
 */
@Composable
private fun EmptyDiscoveryCarousel(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/DevicesMorph.json"))

    // Programmatic frame-accurate progress and alpha state for 1:1 desktop loop playback
    val lottieProgress = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val lottieAlpha = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }

    LaunchedEffect(composition) {
        val comp = composition ?: return@LaunchedEffect

        val baseFrameIncrement = 1f / ANIMATION_FPS
        val rampLengthFrames = SETTLE_FRAME - RAMP_START_FRAME

        while (true) {
            // 1. Initial State: Monitor start frame
            lottieProgress.floatValue = 0f
            lottieAlpha.floatValue = 1f

            var currentFrame = 0f
            var lastNanoTime = System.nanoTime()

            // 2. Play forward from frame 0 up to SETTLE_FRAME (455)
            while (currentFrame < SETTLE_FRAME) {
                withFrameNanos { now ->
                    val deltaSeconds = (now - lastNanoTime) / 1_000_000_000f
                    lastNanoTime = now

                    // Ritardando: decelerate starting at RAMP_START_FRAME down to MIN_SPEED
                    val speed = if (currentFrame < RAMP_START_FRAME) {
                        1.0f
                    } else {
                        val progressIntoRamp = ((currentFrame - RAMP_START_FRAME) / rampLengthFrames).coerceIn(0f, 1f)
                        1.0f - progressIntoRamp * (1.0f - MIN_SPEED)
                    }

                    currentFrame += deltaSeconds * ANIMATION_FPS * speed
                    if (currentFrame > SETTLE_FRAME) {
                        currentFrame = SETTLE_FRAME
                    }
                    lottieProgress.floatValue = (currentFrame / TOTAL_FRAMES).coerceIn(0f, 1f)
                }
            }

            // 3. Settle on DeX: hold still on frame 455 for HOLD_ON_DEX_MS (4s)
            lottieProgress.floatValue = SETTLE_PROGRESS
            delay(HOLD_ON_DEX_MS)

            // 4. Fade out settled DeX to blank (600ms)
            val fadeOutStart = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - fadeOutStart
                val t = (elapsed.toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
                lottieAlpha.floatValue = 1f - t
                if (t >= 1f) break
                withFrameNanos { }
            }

            // 5. Snap to frame 0 while completely invisible
            lottieProgress.floatValue = 0f

            // 6. Fade in monitor start frame (600ms)
            val fadeInStart = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - fadeInStart
                val t = (elapsed.toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
                lottieAlpha.floatValue = t
                if (t >= 1f) break
                withFrameNanos { }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseRing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Centered & Scaled Morph Animation Box
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = lottieAlpha.floatValue
                },
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition = composition,
                progress = { lottieProgress.floatValue },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Clean typography matching modern radar discovery
        Text(
            text = "Searching for nearby devices...",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Make sure DeX is open on your other devices",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Connected State: Swipeable Horizontal Carousel with Center-Snapping Physics,
 * Proximity Depth Curve (Scaling & Opacity), Focused Entry Single-Playback with Tap-to-Replay,
 * and a QR Code "Add Device" card at the end.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectedDevicesCarousel(
    devices: List<DiscoveredDevice>,
    selectedDevice: DiscoveredDevice?,
    backdrop: Backdrop?,
    onDeviceSelect: (DiscoveredDevice) -> Unit,
    onDeviceLongClick: (DiscoveredDevice) -> Unit,
    onAddDeviceClick: () -> Unit,
    uploadState: UploadState,
    downloadState: DownloadState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val view = LocalView.current

    val itemCardWidth = 165.dp
    val centeredIndexState = remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // Total items count = connected devices + 1 (for Add Device QR card)
    val totalItemsCount = devices.size + 1

    // Center Item Tracking with Micro-Haptics & Auto-Selection
    LaunchedEffect(listState, devices) {
        var isFirstEmission = true
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo.minByOrNull { item ->
                val itemCenter = item.offset + item.size / 2
                abs(itemCenter - viewportCenter)
            }?.index
        }
            .distinctUntilChanged()
            .collect { centeredIndex ->
                if (centeredIndex != null && centeredIndex in 0 until totalItemsCount) {
                    val prevIndex = centeredIndexState.intValue
                    centeredIndexState.intValue = centeredIndex
                    if (centeredIndex in devices.indices) {
                        val target = devices[centeredIndex]
                        onDeviceSelect(target)
                    }
                    if (!isFirstEmission && listState.isScrollInProgress && prevIndex != centeredIndex) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    isFirstEmission = false
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(225.dp),
        contentAlignment = Alignment.Center
    ) {
        // Center padding so first and last device cards snap dead center
        val sidePadding = ((maxWidth - itemCardWidth) / 2).coerceAtLeast(16.dp)

        LazyRow(
            state = listState,
            flingBehavior = snapFlingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(devices, key = { _, device -> device.info.fingerprint.ifEmpty { device.ip } }) { index, device ->
                val isSelected = selectedDevice?.info?.fingerprint == device.info.fingerprint
                val isFocused = (centeredIndexState.intValue == index)
                val isPaired = AuthState.pairedFingerprints.contains(device.info.fingerprint)

                val isUploadingToThis = uploadState.isUploading && (uploadState.targetFingerprint == device.info.fingerprint || uploadState.targetFingerprint.isNullOrEmpty())
                val isDownloadingFromThis = downloadState.isDownloading && (downloadState.sourceFingerprint == device.info.fingerprint || downloadState.sourceFingerprint.isNullOrEmpty())
                val isTransferring = isUploadingToThis || isDownloadingFromThis
                val progress = if (isUploadingToThis) uploadState.aggregateProgress else downloadState.progress
                val speedBps = if (isUploadingToThis) uploadState.speedBps else downloadState.speedBps

                // Proximity Depth Scaling & Opacity based on distance from viewport center
                val proximityValues by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                        if (visibleItem != null) {
                            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                            val itemCenter = visibleItem.offset + visibleItem.size / 2f
                            val distance = abs(viewportCenter - itemCenter)
                            val maxDistance = visibleItem.size * 1.5f
                            val proximity = (1f - (distance / maxDistance)).coerceIn(0f, 1f)

                            val scale = 0.85f + (0.23f * proximity) // 0.85x at edges -> 1.08x at center
                            val alpha = 0.55f + (0.45f * proximity) // 0.55 at edges -> 1.0 at center
                            scale to alpha
                        } else {
                            0.85f to 0.55f
                        }
                    }
                }

                DeviceCarouselCard(
                    device = device,
                    isSelected = isSelected,
                    isFocused = isFocused,
                    isPaired = isPaired,
                    isTransferring = isTransferring,
                    transferProgress = progress,
                    transferSpeedBps = speedBps,
                    proximityScale = proximityValues.first,
                    proximityAlpha = proximityValues.second,
                    onClick = {
                        onDeviceSelect(device)
                        if (!isFocused) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                    },
                    onLongClick = { onDeviceLongClick(device) },
                )
            }

            // QR Code "Add Device" Action Card at the end of the carousel
            item(key = "add_device_card") {
                val addDeviceIndex = devices.size
                val isFocused = (centeredIndexState.intValue == addDeviceIndex)

                val proximityValues by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == addDeviceIndex }
                        if (visibleItem != null) {
                            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                            val itemCenter = visibleItem.offset + visibleItem.size / 2f
                            val distance = abs(viewportCenter - itemCenter)
                            val maxDistance = visibleItem.size * 1.5f
                            val proximity = (1f - (distance / maxDistance)).coerceIn(0f, 1f)

                            val scale = 0.85f + (0.23f * proximity)
                            val alpha = 0.55f + (0.45f * proximity)
                            scale to alpha
                        } else {
                            0.85f to 0.55f
                        }
                    }
                }

                AddDeviceCarouselCard(
                    isFocused = isFocused,
                    proximityScale = proximityValues.first,
                    proximityAlpha = proximityValues.second,
                    onClick = {
                        if (!isFocused) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(addDeviceIndex)
                            }
                        }
                        onAddDeviceClick()
                    }
                )
            }
        }
    }
}

/**
 * Modern QR Code "Add Device" Card at the end of the carousel.
 */
@Composable
private fun AddDeviceCarouselCard(
    isFocused: Boolean,
    proximityScale: Float,
    proximityAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val circleBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val iconTint = if (isFocused) MaterialTheme.colorScheme.primary else if (isDark) Color.White else Color.Black

    Column(
        modifier = modifier
            .width(165.dp)
            .height(200.dp)
            .graphicsLayer {
                scaleX = proximityScale
                scaleY = proximityScale
                alpha = proximityAlpha
            }
            .bubbleFluidity(targetScale = 1.05f, pullFactor = 0.05f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Top Area: Circular container with QR Scanner icon
        Box(
            modifier = Modifier.size(145.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(circleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MaterialSymbols.QrCodeScanner,
                    contentDescription = "Add Device",
                    tint = iconTint,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Bottom Area: "Add Device" Title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Add Device",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Frameless Big Device Presentation with Single-Shot Entry Animation, Tap-to-Replay, and Paused Idle State.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceCarouselCard(
    device: DiscoveredDevice,
    isSelected: Boolean,
    isFocused: Boolean,
    isPaired: Boolean,
    isTransferring: Boolean,
    transferProgress: Float,
    transferSpeedBps: Long,
    proximityScale: Float,
    proximityAlpha: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deviceTypeLower = device.info.deviceType.lowercase()
    val lottieAsset = when {
        deviceTypeLower.contains("laptop") || deviceTypeLower.contains("desktop") || deviceTypeLower.contains("pc") || deviceTypeLower.contains("mac") -> "lottie/laptop_connected.json"
        deviceTypeLower.contains("tablet") || deviceTypeLower.contains("ipad") -> "lottie/tablet_connected.json"
        deviceTypeLower.contains("watch") -> "lottie/watch_connected.json"
        else -> "lottie/device_connected.json"
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(lottieAsset))
    val lottieAnimatable = rememberLottieAnimatable()
    var tapReplayCount by remember { mutableIntStateOf(0) }

    // Smooth animation lifecycle: Entry play (1 iteration) on focus/tap, smoothly finish to completion if swiped away mid-play, paused on idle
    LaunchedEffect(composition, isFocused, tapReplayCount) {
        val comp = composition ?: return@LaunchedEffect
        if (isFocused) {
            lottieAnimatable.animate(
                composition = comp,
                iterations = 1,
                initialProgress = 0f,
            )
        } else {
            // If swiped away while mid-animation, smoothly finish playing to 1f instead of abruptly snapping
            if (lottieAnimatable.progress > 0f && lottieAnimatable.progress < 0.99f) {
                lottieAnimatable.animate(
                    composition = comp,
                    iterations = 1,
                    initialProgress = lottieAnimatable.progress,
                )
            } else {
                lottieAnimatable.snapTo(composition = comp, progress = 1f)
            }
        }
    }

    Column(
        modifier = modifier
            .width(165.dp)
            .height(200.dp)
            .graphicsLayer {
                scaleX = proximityScale
                scaleY = proximityScale
                alpha = proximityAlpha
            }
            .bubbleFluidity(targetScale = 1.05f, pullFactor = 0.05f)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = {
                    // Only trigger replay if the animation has completely finished playing (ignore taps while animating)
                    if (isFocused && !lottieAnimatable.isPlaying && lottieAnimatable.progress >= 0.99f) {
                        tapReplayCount++
                    }
                    onClick()
                },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Top Area: Big Centered Lottie Graphic with optional transfer progress ring
        Box(
            modifier = Modifier.size(145.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isTransferring) {
                CircularProgressIndicator(
                    progress = { transferProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(140.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.5.dp,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
            }

            LottieAnimation(
                composition = composition,
                progress = { lottieAnimatable.progress },
                modifier = Modifier.size(135.dp),
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Bottom Area: Device Alias & Live Telemetry / Status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = device.info.alias.ifEmpty { device.info.deviceModel },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            val subtitleText = when {
                isTransferring -> {
                    val mbps = transferSpeedBps / (1024f * 1024f)
                    if (mbps >= 0.1f) String.format(java.util.Locale.US, "%.1f MB/s", mbps) else "Transferring..."
                }
                device.info.battery != null -> "${device.info.battery}% Battery"
                isPaired -> "Paired"
                else -> null
            }

            if (subtitleText != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleText,
                    fontSize = 12.sp,
                    color = if (isTransferring || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isTransferring || isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
