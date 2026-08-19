package com.dexstudios.dex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dexstudios.dex.network.*
import com.dexstudios.dex.ui.components.DeXButton
import com.dexstudios.dex.ui.components.DeXTextButton
import com.dexstudios.dex.ui.components.DeviceListItem
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

class ShareTargetActivity : ComponentActivity() {

    private val sharedUris = mutableStateListOf<Uri>()
    private val discoveryEngine: DiscoveryEngine by inject()
    private val clientEngine: ClientEngine by inject()
    private val deviceConfig: DeviceConfig by inject()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ -> }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Handle incoming intent
        val incomingUris = mutableListOf<Uri>()
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { incomingUris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                uris?.let { incomingUris.addAll(it) }
            }
        }
        sharedUris.addAll(incomingUris)

        if (sharedUris.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_no_files), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val targetFingerprint = intent?.getStringExtra("EXTRA_TARGET_FINGERPRINT")
        if (targetFingerprint != null) {
            val device = discoveryEngine.devices.value[targetFingerprint]
            if (device != null) {
                sendUrisToDevice(device, sharedUris)
                finish()
            } else {
                Toast.makeText(this, getString(R.string.share_pc_offline), Toast.LENGTH_LONG).show()
                saveToSandbox()
            }
            return
        }

        setContent {
            MaterialTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var showSheet by remember { mutableStateOf(true) }
                val discoveredDevices by discoveryEngine.devices.collectAsStateWithLifecycle()
                val uploadState by clientEngine.uploadState.collectAsStateWithLifecycle()

                val (trustedLocal, untrustedDevices) = remember(discoveredDevices) {
                    discoveredDevices.values.partition { device ->
                        AuthState.pairedFingerprints.contains(device.info.fingerprint) ||
                                (device.info.identityHash != null && device.info.identityHash == deviceConfig.identityHash)
                    }
                }

                if (showSheet) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            showSheet = false
                            finish()
                        },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    ) {
                        if (uploadState.isUploading || uploadState.isSuccess || uploadState.error != null) {
                            UploadProgressScreen(uploadState)
                        } else {
                            ShareTargetScreen(
                                trustedDevices = trustedLocal,
                                untrustedDevices = untrustedDevices,
                                onSaveToSandbox = {
                                    saveToSandbox()
                                    showSheet = false
                                },
                                onSendToDevice = { device ->
                                    sendUrisToDevice(device, sharedUris)
                                    clientEngine.resetUploadState()
                                    startActivity(Intent(this@ShareTargetActivity, MainActivity::class.java))
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ShareTargetScreen(
        trustedDevices: List<DiscoveredDevice>,
        untrustedDevices: List<DiscoveredDevice>,
        onSaveToSandbox: () -> Unit,
        onSendToDevice: (DiscoveredDevice) -> Unit
    ) {
        val totalSize = remember(sharedUris) { sharedUris.sumOf { getFileSize(it) } }
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

    @Composable
    private fun CompactDeviceCard(device: DiscoveredDevice, onClick: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(100.dp)
                .bubbleFluidity(targetScale = 0.95f)
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

    @Composable
    fun UploadProgressScreen(uploadState: UploadState) {
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
                    DeXTextButton(onClick = { clientEngine.cancelUpload(this@ShareTargetActivity) }) {
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
                    DeXButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
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
                    DeXButton(onClick = { clientEngine.resetUploadState() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Try Again")
                    }
                }
            }
        }
    }

    private fun saveToSandbox() {
        val dirUri = SafStorage.getDownloadsDexUri(this)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    var successCount = 0
                    sharedUris.forEach { uri ->
                        val fileName = getFileName(uri)
                        contentResolver.openInputStream(uri)?.use { input ->
                            val ok = if (dirUri != null) {
                                SafStorage.writeFile(this@ShareTargetActivity, dirUri, fileName, input)
                            } else {
                                val mediaUri = SafStorage.createMediaStoreUri(this@ShareTargetActivity, fileName)
                                if (mediaUri != null) {
                                    contentResolver.openOutputStream(mediaUri)?.use { out -> input.copyTo(out) }
                                    true
                                } else false
                            }
                            if (ok) successCount++
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (successCount == sharedUris.size) {
                            Toast.makeText(this@ShareTargetActivity, "Saved to DeX Sandbox", Toast.LENGTH_SHORT).show()
                        } else if (successCount > 0) {
                            Toast.makeText(this@ShareTargetActivity, "Saved $successCount of ${sharedUris.size} files", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@ShareTargetActivity, "Failed to save files. Grant folder access in Settings.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ShareTargetActivity, "Failed to save files", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            finish()
        }
    }

    private fun sendUrisToDevice(device: DiscoveredDevice, uris: List<Uri>) {
        clientEngine.resetUploadState()

        val urisJson = try {
            Json.encodeToString(sharedUris.map { it.toString() })
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        val inputData = workDataOf(
            "ip" to device.ip,
            "port" to device.info.port,
            "uris" to urisJson,
            "targetFingerprint" to device.info.fingerprint,
            "targetAlias" to device.info.alias
        )

        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        clientEngine.activeWorkId = workRequest.id
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "SharedFile_${System.currentTimeMillis()}"
    }

    private fun getFileSize(uri: Uri): Long {
        var result: Long = 0
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index >= 0) {
                            result = cursor.getLong(index)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return result
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return java.util.Locale.ROOT.let { String.format(it, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups]) }
    }
}
