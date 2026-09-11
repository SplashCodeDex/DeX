package com.dexstudios.dex.ui.components

import android.app.DownloadManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.network.DownloadState
import com.dexstudios.dex.network.GoogleProfile
import com.dexstudios.dex.network.UploadState
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.util.Formatters

enum class IslandContentState {
    IDLE,
    NAME_PILL_PROFILE,
    EXPANDED_PROFILE,
    COLLAPSED_TRANSFER,
    EXPANDED_TRANSFER,
    EXPANDED_SELECTION
}

@Composable
fun CollapsedProfileContent(
    profile: GoogleProfile,
    modifier: Modifier = Modifier
) {
    if (profile.picture.isNotBlank()) {
        AsyncImage(
            model = profile.picture,
            contentDescription = "Profile",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else if (profile.email.isNotBlank()) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val initial = profile.name.ifBlank { profile.email }.firstOrNull()?.uppercase()
            if (initial != null) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MaterialSymbols.AccountCircle,
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun ProfileNamePillContent(
    profile: GoogleProfile,
    isPro: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (profile.picture.isNotBlank()) {
            AsyncImage(
                model = profile.picture,
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
            )
        } else if (profile.email.isNotBlank()) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val initial = profile.name.ifBlank { profile.email }.firstOrNull()?.uppercase()
                if (initial != null) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MaterialSymbols.AccountCircle,
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = profile.name.ifBlank { "User" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (isPro) "PRO" else "FREE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun TransferIcon(
    isDownloading: Boolean,
    isUploading: Boolean,
    modifier: Modifier = Modifier,
    peerPicture: String? = null
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val infiniteTransition = rememberInfiniteTransition(label = "transferRotation")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        // Spinning Frame (Dotted Circle)
        Icon(
            imageVector = MaterialSymbols.ArrowUploadCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
        )

        val translationY = remember { Animatable(0f) }

        LaunchedEffect(isUploading, isDownloading) {
            while (true) {
                if (isUploading) {
                    translationY.snapTo(10f)
                    translationY.animateTo(
                        targetValue = -10f,
                        animationSpec = tween(1500, easing = LinearOutSlowInEasing)
                    )
                } else if (isDownloading) {
                    translationY.snapTo(-10f)
                    translationY.animateTo(
                        targetValue = 10f,
                        animationSpec = tween(1500, easing = LinearOutSlowInEasing)
                    )
                } else {
                    translationY.animateTo(0f)
                    break
                }
            }
        }

        Icon(
            imageVector = if (isUploading) MaterialSymbols.ArrowUploadArrow else MaterialSymbols.ArrowDownloadArrow,
            contentDescription = if (isUploading) "Uploading" else "Downloading",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.translationY = translationY.value.dp.toPx()
                }
        )

        // Peer Avatar Overlay (AirDrop Style)
        if (!peerPicture.isNullOrBlank()) {
            AsyncImage(
                model = peerPicture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.Black, CircleShape)
            )
        }
    }
}

@Composable
fun TransferProgressRing(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "transferRingProgress"
    )
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        // Track
        drawCircle(
            color = primaryColor.copy(alpha = 0.1f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        // Progress
        drawArc(
            color = primaryColor,
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun ExpandedTransferContent(
    downloadState: DownloadState,
    uploadState: UploadState,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val isDownloadActive = downloadState.isDownloading || downloadState.isSuccess
    val showDownload = isDownloadActive

    val isSuccess = if (showDownload) downloadState.isSuccess else uploadState.isSuccess
    val progress = if (showDownload) downloadState.progress else uploadState.aggregateProgress
    val peerName = if (showDownload) downloadState.peerName else uploadState.peerName
    val peerPicture = if (showDownload) downloadState.peerPicture else uploadState.peerPicture
    val totalFiles = if (showDownload) downloadState.totalFiles else uploadState.totalFiles
    val fileName = if (showDownload) downloadState.fileName else uploadState.fileName
    val speedBps = if (showDownload) downloadState.speedBps else uploadState.speedBps

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            TransferIcon(
                isDownloading = showDownload && !isSuccess,
                isUploading = !showDownload && !isSuccess,
                peerPicture = peerPicture,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (showDownload) "Incoming Transfer" else "Outgoing Transfer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = buildString {
                        if (!peerName.isNullOrBlank()) append("$peerName ")
                        append(if (showDownload) "is sharing " else "is receiving ")
                        append(if (totalFiles > 1) "$totalFiles files" else "a file")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showDownload) MaterialSymbols.FileDownload else MaterialSymbols.FileUpload,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSuccess) "Transfer Complete" else "${(progress * 100).toInt()}% • ${formatSpeed(speedBps)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (isSuccess) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
            if (!isSuccess) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (isSuccess) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f))
                .clickable {
                    if (isSuccess) {
                        try {
                            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) {
                            Toast.makeText(context, "Cannot open downloads", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        onCancel()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSuccess) "Open Folder" else "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = if (isSuccess) MaterialTheme.colorScheme.onPrimary else Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun ExpandedProfileContent(
    profile: GoogleProfile,
    onSignIn: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (profile.email.isBlank()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Sign in to sync your devices",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                DeXButton(
                    onClick = onSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Google,
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Sign in with Google",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            AsyncImage(
                model = profile.picture,
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name.ifBlank { "User" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatSpeed(bps: Long): String = when {
    bps >= 1024L * 1024 * 1024 -> java.util.Locale.ROOT.let { String.format(it, "%.1f GB/s", bps / (1024f * 1024 * 1024)) }
    bps >= 1024L * 1024 -> java.util.Locale.ROOT.let { String.format(it, "%.1f MB/s", bps / (1024f * 1024)) }
    bps >= 1024L -> java.util.Locale.ROOT.let { String.format(it, "%.0f KB/s", bps / 1024f) }
    else -> "$bps B/s"
}

@Composable
fun ExpandedSelectionDispatchContent(
    selectedUris: List<Uri>,
    totalSizeBytes: Long,
    devices: List<DiscoveredDevice>,
    onSendToDevice: (DiscoveredDevice) -> Unit,
    onDismiss: () -> Unit,
    onPairDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val dismissThresholdPx = with(density) { 44.dp.toPx() }

    // Interactive Drag-to-Dismiss Gesture state
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 380f
        ),
        label = "dragOffsetY"
    )

    // Apple Staggered Pop-in Animators (25ms cascade)
    val thumbPopProgress = remember { Animatable(0f) }
    val textPopProgress = remember { Animatable(0f) }
    val dismissPopProgress = remember { Animatable(0f) }
    val devicesPopProgress = remember { Animatable(0f) }

    val popSpec = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 380f
    )

    LaunchedEffect(Unit) {
        launch { thumbPopProgress.animateTo(1f, popSpec) }
        delay(25)
        launch { textPopProgress.animateTo(1f, popSpec) }
        delay(25)
        launch { dismissPopProgress.animateTo(1f, popSpec) }
        delay(25)
        launch { devicesPopProgress.animateTo(1f, popSpec) }
    }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                if (dragAmount > 0f) {
                    // Downward drag with fluid 0.25f rubberband resistance
                    dragOffsetY = (dragOffsetY + dragAmount * 0.25f).coerceAtLeast(0f)
                } else {
                    // Upward tension resistance
                    dragOffsetY = (dragOffsetY + dragAmount * 0.10f)
                }
            },
            onDragEnd = {
                if (dragOffsetY >= dismissThresholdPx) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    dragOffsetY = 0f
                    onDismiss()
                } else {
                    dragOffsetY = 0f
                }
            },
            onDragCancel = {
                dragOffsetY = 0f
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(dragModifier)
            .graphicsLayer {
                translationY = animatedDragOffsetY
                alpha = (1f - (animatedDragOffsetY / (dismissThresholdPx * 2.5f))).coerceIn(0.4f, 1f)
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top row: items telemetry and preview on the left, dismiss button on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stack of preview thumbnails (up to 3) with staggered pop-in
                if (selectedUris.isNotEmpty()) {
                    val previewUris = selectedUris.take(3)
                    val stackWidth = 36.dp + ((previewUris.size - 1) * 24).dp
                    val thumbProgress = thumbPopProgress.value
                    Box(
                        modifier = Modifier
                            .size(width = stackWidth, height = 36.dp)
                            .graphicsLayer {
                                scaleX = 0.85f + 0.15f * thumbProgress
                                scaleY = 0.85f + 0.15f * thumbProgress
                                alpha = thumbProgress.coerceIn(0f, 1f)
                                translationX = with(density) { ((1f - thumbProgress) * -12).dp.toPx() }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        previewUris.forEachIndexed { index, uri ->
                            Box(
                                modifier = Modifier
                                    .padding(start = (index * 24).dp)
                                    .size(36.dp)
                                    .zIndex((3 - index).toFloat())
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                val textProgress = textPopProgress.value
                Column(
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.90f + 0.10f * textProgress
                        scaleY = 0.90f + 0.10f * textProgress
                        alpha = textProgress.coerceIn(0f, 1f)
                        translationX = with(density) { ((1f - textProgress) * -8).dp.toPx() }
                    }
                ) {
                    val itemCount = selectedUris.size
                    Text(
                        text = "$itemCount item${if (itemCount != 1) "s" else ""} selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (totalSizeBytes > 0L) {
                        Text(
                            text = Formatters.formatBytes(totalSizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val dismissProgress = dismissPopProgress.value
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = 0.85f + 0.15f * dismissProgress
                    scaleY = 0.85f + 0.15f * dismissProgress
                    alpha = dismissProgress.coerceIn(0f, 1f)
                }
            ) {
                DynamicDismissButton(
                    onClick = onDismiss,
                    size = DynamicDismissButtonSize.Medium,
                    colors = DynamicDismissButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White
                    )
                )
            }
        }

        // Bottom row: target device chips with staggered pop-in
        val devicesProgress = devicesPopProgress.value
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = 0.92f + 0.08f * devicesProgress
                    scaleY = 0.92f + 0.08f * devicesProgress
                    alpha = devicesProgress.coerceIn(0f, 1f)
                    translationY = with(density) { ((1f - devicesProgress) * 8).dp.toPx() }
                }
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (devices.isEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .bubbleFluidity()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPairDevice
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Devices,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Pair New Device",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            } else {
                devices.forEach { device ->
                    val rawAlias = device.info.alias.ifBlank { device.info.deviceModel }
                    val deviceName = rawAlias.ifBlank { "DeX Device" }
                    val isPc = device.info.deviceType.contains("pc", ignoreCase = true) ||
                            device.info.deviceType.contains("desktop", ignoreCase = true) ||
                            device.info.deviceModel.contains("windows", ignoreCase = true) ||
                            device.info.deviceModel.contains("mac", ignoreCase = true)
                    val deviceIcon = if (isPc) MaterialSymbols.Computer else MaterialSymbols.Devices

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .bubbleFluidity()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSendToDevice(device) }
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Send to $deviceName",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
