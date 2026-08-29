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
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dexstudios.dex.network.AuthState
import com.dexstudios.dex.network.ClientEngine
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.network.DownloadState
import com.dexstudios.dex.network.UploadState
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassTokens
import com.dexstudios.dex.ui.components.glass.shinyGlare
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    val morphComposition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie/DevicesMorph.json")
    )

    val lottieProgress = remember { Animatable(0f) }
    val lottieAlpha = remember { Animatable(1f) }

    // Desktop parity precision frame loop:
    // ritardando settle -> hold on DeX -> fade to blank -> monitor fades in -> repeat
    LaunchedEffect(morphComposition) {
        if (morphComposition == null) return@LaunchedEffect
        lottieProgress.snapTo(0f)
        lottieAlpha.snapTo(1f)
        while (true) {
            // 1. Play frames 0..455, decelerating as DeX pops in
            var lastNanos = withFrameNanos { it }
            while (lottieProgress.value < SETTLE_PROGRESS) {
                val nanos = withFrameNanos { it }
                val dt = (nanos - lastNanos) / 1_000_000_000f
                lastNanos = nanos
                val currentFrame = lottieProgress.value * TOTAL_FRAMES
                val speed = if (currentFrame > RAMP_START_FRAME && currentFrame < SETTLE_FRAME) {
                    val t = (currentFrame - RAMP_START_FRAME) / (SETTLE_FRAME - RAMP_START_FRAME)
                    1f - (1f - MIN_SPEED) * t * t
                } else {
                    1f
                }
                lottieProgress.snapTo(
                    (lottieProgress.value + speed * ANIMATION_FPS * dt / TOTAL_FRAMES)
                        .coerceAtMost(SETTLE_PROGRESS)
                )
            }

            // 2. Hold on the fully settled DeX for 4 seconds
            delay(HOLD_ON_DEX_MS)

            // 3. DeX fades out completely
            lottieAlpha.animateTo(0f, tween(FADE_OUT_MS))

            // 4. Jump back to frame 0
            lottieProgress.snapTo(0f)

            // 5. Fade back in
            lottieAlpha.animateTo(1f, tween(FADE_IN_MS))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Large centered morph animation (200dp x 200dp)
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    alpha = lottieAlpha.value
                },
            contentAlignment = Alignment.Center,
        ) {
            LottieAnimation(
                composition = morphComposition,
                progress = { lottieProgress.value },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Searching for nearby devices...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Connected State: Swipeable Horizontal Carousel of Liquid Glass Device Cards with live Lottie animations & transfer feedback.
 */
@Composable
private fun ConnectedDevicesCarousel(
    devices: List<DiscoveredDevice>,
    selectedDevice: DiscoveredDevice?,
    backdrop: Backdrop?,
    onDeviceSelect: (DiscoveredDevice) -> Unit,
    onDeviceLongClick: (DiscoveredDevice) -> Unit,
    uploadState: UploadState,
    downloadState: DownloadState,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(devices, key = { it.info.fingerprint.ifEmpty { it.ip } }) { device ->
            val isSelected = selectedDevice?.info?.fingerprint == device.info.fingerprint
            val isPaired = AuthState.pairedFingerprints.contains(device.info.fingerprint)

            val isUploadingToThis = uploadState.isUploading && (uploadState.targetFingerprint == device.info.fingerprint || uploadState.targetFingerprint.isNullOrEmpty())
            val isDownloadingFromThis = downloadState.isDownloading && (downloadState.sourceFingerprint == device.info.fingerprint || downloadState.sourceFingerprint.isNullOrEmpty())
            val isTransferring = isUploadingToThis || isDownloadingFromThis
            val progress = if (isUploadingToThis) uploadState.aggregateProgress else downloadState.progress
            val speedBps = if (isUploadingToThis) uploadState.speedBps else downloadState.speedBps

            DeviceCarouselCard(
                device = device,
                isSelected = isSelected,
                isPaired = isPaired,
                isTransferring = isTransferring,
                transferProgress = progress,
                transferSpeedBps = speedBps,
                backdrop = backdrop,
                onClick = { onDeviceSelect(device) },
                onLongClick = { onDeviceLongClick(device) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceCarouselCard(
    device: DiscoveredDevice,
    isSelected: Boolean,
    isPaired: Boolean,
    isTransferring: Boolean,
    transferProgress: Float,
    transferSpeedBps: Long,
    backdrop: Backdrop?,
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
    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .width(135.dp)
            .height(165.dp)
            .bubbleFluidity(targetScale = 1.04f, pullFactor = 0.05f)
            .clip(cardShape)
            .border(
                width = if (isTransferring) 2.dp else if (isSelected) 1.5.dp else 0.dp,
                color = if (isTransferring) MaterialTheme.colorScheme.primary else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.Transparent,
                shape = cardShape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (backdrop != null) {
            LiquidGlassPanel(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxSize()
                    .shinyGlare(
                        shape = cardShape,
                        intensity = if (isTransferring || isSelected) LiquidGlassTokens.GlareFactor else LiquidGlassTokens.GlareRestAlpha * 0.5f,
                    ),
                shape = cardShape,
                config = LiquidGlassPresets.IconButton.copy(
                    shape = cardShape,
                    blurRadius = 12.dp,
                    surfaceTint = if (isTransferring || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    surfaceTintAlpha = if (isTransferring) 0.35f else if (isSelected) 0.28f else 0.15f,
                ),
                content = {}
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Row: Status Indicator Dot & Telemetry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Connection Status Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = if (isTransferring) Color(0xFF2196F3) else if (isPaired) Color(0xFF4CAF50) else Color(0xFFFFB300)
                        )
                    }
                }

                if (isTransferring) {
                    Text(
                        text = "${(transferProgress * 100).roundToInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (device.info.battery != null) {
                    Text(
                        text = "${device.info.battery}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                }
            }

            // Center Lottie Graphic with optional transfer progress ring
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isTransferring) {
                    CircularProgressIndicator(
                        progress = { transferProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(76.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    )
                }

                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.size(68.dp),
                )
            }

            // Bottom Device Alias & Transfer Speed / Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = device.info.alias.ifEmpty { device.info.deviceModel },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (isTransferring) {
                        val mbps = transferSpeedBps / (1024f * 1024f)
                        if (mbps >= 0.1f) String.format(java.util.Locale.US, "%.1f MB/s", mbps) else "Transferring..."
                    } else if (isPaired) "Paired" else "Discovered",
                    fontSize = 10.sp,
                    color = if (isTransferring) MaterialTheme.colorScheme.primary else if (isPaired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isTransferring) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
