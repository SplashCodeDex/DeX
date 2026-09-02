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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import coil3.compose.AsyncImage
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DownloadState
import com.dexstudios.dex.network.GoogleProfile
import com.dexstudios.dex.network.UploadState
import com.dexstudios.dex.ui.icons.MaterialSymbols

enum class IslandContentState {
    IDLE,
    EXPANDED_PROFILE,
    COLLAPSED_TRANSFER,
    EXPANDED_TRANSFER
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
            val initial = profile.name.ifBlank { profile.email }.firstOrNull()?.uppercase()?.toString()
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
