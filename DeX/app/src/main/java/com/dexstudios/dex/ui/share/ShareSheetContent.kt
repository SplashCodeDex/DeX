package com.dexstudios.dex.ui.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.network.UploadState
import com.dexstudios.dex.ui.components.DeXButton
import com.dexstudios.dex.ui.components.DeXTextButton
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.icons.MaterialSymbols

/**
 * Device picker body for the share flow. Host-agnostic by design: it renders
 * identically inside the activity-hosted bottom sheet and inside the system
 * overlay panel ([ShareOverlayWindow]), so both surfaces stay behaviorally
 * identical by construction instead of by duplicated markup.
 */
@Composable
fun ShareTargetSheetContent(
    sharedUris: List<Uri>,
    trustedDevices: List<DiscoveredDevice>,
    untrustedDevices: List<DiscoveredDevice>,
    showOverlayOptIn: Boolean,
    onEnableOverlay: () -> Unit,
    onSaveToSandbox: () -> Unit,
    onSendToDevice: (DiscoveredDevice) -> Unit
) {
    val context = LocalContext.current
    val totalSize = remember(sharedUris, context) { sharedUris.sumOf { resolveFileSize(context, it) } }
    val sizeStr = remember(totalSize) { formatSize(totalSize) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp, top = 24.dp)
    ) {
        Text(
            text = "Send to Device",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${sharedUris.size} file(s) • $sizeStr",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (trustedDevices.isNotEmpty()) {
            Text("Trusted Devices", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(trustedDevices, key = { it.info.fingerprint }) { device ->
                    CompactDeviceCard(device, onClick = { onSendToDevice(device) })
                }
            }
        }

        if (untrustedDevices.isNotEmpty()) {
            Text("Discovered", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(untrustedDevices, key = { it.info.fingerprint }) { device ->
                    CompactDeviceCard(device, onClick = { onSendToDevice(device) })
                }
            }
        }

        if (trustedDevices.isEmpty() && untrustedDevices.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("No devices found on LAN", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (showOverlayOptIn) {
            OverlayOptInRow(onClick = onEnableOverlay)
            Spacer(modifier = Modifier.height(12.dp))
        }

        DeXButton(
            onClick = onSaveToSandbox,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
        ) {
            Icon(MaterialSymbols.Folder, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Save to Local DeX Sandbox", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * One-tap route to the system "Display over other apps" settings. Shown only while
 * the permission is missing — once granted, the share panel upgrades to a true
 * overlay and this row disappears with the fallback branch.
 */
@Composable
private fun OverlayOptInRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            MaterialSymbols.IosShare,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.share_enable_overlay_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.share_enable_overlay_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CompactDeviceCard(device: DiscoveredDevice, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .bubbleFluidity(targetScale = 0.95f, pullFactor = 0.02f)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
        ) {
            Icon(
                imageVector = if (device.info.deviceType == "desktop") MaterialSymbols.Computer else MaterialSymbols.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = device.info.alias,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Upload lifecycle body (progress / success / failure) shared by both hosts.
 * All state transitions are injected so neither the activity nor the overlay
 * window needs to know how cancellation or retry is performed.
 */
@Composable
fun UploadProgressContent(
    uploadState: UploadState,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp, top = 24.dp)
    ) {
        if (uploadState.isUploading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(pluralStringResource(R.plurals.uploading_progress, uploadState.totalFiles, uploadState.currentFileIndex, uploadState.totalFiles), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                DeXTextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(uploadState.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { uploadState.aggregateProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("${(uploadState.aggregateProgress * 100).toInt()}% Total", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.End), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        } else if (uploadState.isSuccess) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Transfer Complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(24.dp))
                DeXButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        } else if (uploadState.error != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(MaterialSymbols.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Upload Failed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(uploadState.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                DeXButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Try Again")
                }
            }
        }
    }
}

internal fun resolveFileSize(context: Context, uri: Uri): Long {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0) {
                        return cursor.getLong(index)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return 0L
}

internal fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return java.util.Locale.ROOT.let { String.format(it, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups]) }
}
