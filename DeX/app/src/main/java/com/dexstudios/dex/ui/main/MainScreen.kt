package com.dexstudios.dex.ui.main

import android.net.Uri
import androidx.core.net.toUri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.dexstudios.dex.network.RegisterDto
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import com.dexstudios.dex.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexstudios.dex.network.AuthState
import com.dexstudios.dex.network.DeXPorts
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.network.PunchState
import com.dexstudios.dex.network.TcpDownloadService
import kotlinx.coroutines.launch
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

import androidx.compose.ui.unit.sp

import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import com.dexstudios.dex.network.WebSocketClientService
import com.dexstudios.dex.network.DeviceManager
import com.dexstudios.dex.ui.components.DeviceListItem
import com.dexstudios.dex.ui.components.NetworkErrorDialog
import com.dexstudios.dex.ui.components.FloatingTopAppBar
import com.dexstudios.dex.ui.components.*
import com.dexstudios.dex.ui.main.components.ScanToAddDeviceCard
import com.dexstudios.dex.ui.main.components.DummyDeviceCard
import com.dexstudios.dex.ui.main.components.MainScreenCompact
import com.dexstudios.dex.ui.main.components.MainScreenGrid
import com.dexstudios.dex.ui.components.glass.GlassScrollEdge
import androidx.compose.ui.text.style.TextAlign
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

@android.annotation.SuppressLint("LocalContextGetResourceValueCall", "UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    viewModel: MainScreenViewModel = koinViewModel(),
    windowSizeClass: WindowSizeClass
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uploadState by viewModel.clientEngine.uploadState.collectAsStateWithLifecycle()
    val downloadState by TcpDownloadService.downloadState.collectAsStateWithLifecycle()
    val rosterDevices by PunchState.devices.collectAsStateWithLifecycle()

    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var selectedRosterDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var pairingDeviceFingerprint by remember { mutableStateOf<String?>(null) }
    var showTroubleshootDialog by remember { mutableStateOf(value = false) }
    var contextMenuDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var connectOptionsDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

    // Seamless Discovery Guidance: Show help hint if no devices found for 15 seconds
    var showHelpHint by remember { mutableStateOf(false) }
    val discoveredDevices = (uiState as? MainScreenUiState.Success)?.data ?: emptyList()

    LaunchedEffect(discoveredDevices) {
        if (discoveredDevices.isEmpty()) {
            delay(15.seconds)
            if (discoveredDevices.isEmpty()) {
                showHelpHint = true
            }
        } else {
            showHelpHint = false
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val launchQrScanner = {
        val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (rawValue != null && rawValue.startsWith("http://")) {
                    val uri = rawValue.toUri()
                    val mainIp = uri.host
                    val port = uri.port.takeIf { it > 0 } ?: DeXPorts.HTTPS
                    val extraIps = uri.getQueryParameter("ips")?.split(",") ?: emptyList()

                    val allIps = listOfNotNull(mainIp) + extraIps

                    if (allIps.isNotEmpty()) {
                        Toast.makeText(context, context.getString(R.string.toast_scanned_ip, allIps.joinToString(", ")), Toast.LENGTH_SHORT).show()
                        allIps.forEach { ip ->
                            viewModel.discoveryEngine.sendManualDiscovery(ip, port)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, context.getString(R.string.toast_scan_failed, e.message.toString()), Toast.LENGTH_SHORT).show()
            }
    }

    // Modern Android Photo/File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        // Same-email device over WAN: send directly via NAT punch (through the PC as rendezvous)
        val rosterTarget = selectedRosterDevice
        if (rosterTarget != null) {
            selectedRosterDevice = null
            Toast.makeText(context, resources.getQuantityString(R.plurals.toast_sending_files, uris.size, uris.size, rosterTarget.info.alias), Toast.LENGTH_SHORT).show()

            val urisJson = try {
                Json.encodeToString(uris.map { it.toString() })
            } catch (e: Exception) {
                e.printStackTrace()
                return@rememberLauncherForActivityResult
            }

            val workRequest = OneTimeWorkRequestBuilder<com.dexstudios.dex.network.PunchSendWorker>()
                .setInputData(
                    workDataOf(
                        "targetFingerprint" to rosterTarget.info.fingerprint,
                        "targetAlias" to rosterTarget.info.alias,
                        "uris" to urisJson
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            return@rememberLauncherForActivityResult
        }

        selectedDevice?.let { device ->
            Toast.makeText(context, resources.getQuantityString(R.plurals.toast_sending_files, uris.size, uris.size, device.info.alias), Toast.LENGTH_SHORT).show()

            viewModel.clientEngine.resetUploadState()

            val urisJson = try {
                Json.encodeToString(uris.map { it.toString() })
            } catch (e: Exception) {
                e.printStackTrace()
                return@let
            }

            val inputData = workDataOf(
                "ip" to device.ip,
                "port" to device.info.port,
                "uris" to urisJson,
                "targetFingerprint" to device.info.fingerprint,
                "targetAlias" to device.info.alias
            ).let { base ->
                val identityHash = device.info.identityHash
                val googleSub = device.info.googleSub
                if (identityHash != null || googleSub != null) {
                    androidx.work.Data.Builder().putAll(base)
                        .apply {
                            identityHash?.let { putString("targetIdentityHash", it) }
                            googleSub?.let { putString("targetGoogleSub", it) }
                        }
                        .build()
                } else {
                    base
                }
            }

            val workRequest = OneTimeWorkRequestBuilder<com.dexstudios.dex.network.UploadWorker>()
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            viewModel.clientEngine.activeWorkId = workRequest.id
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    val contentBackdrop = rememberLayerBackdrop()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Full-window content: the scrolling content passes behind the native
        // status bar and navigation bar — the glass edge fades keep it readable.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        // Status bar height — content scrolls behind the native status bar, so
        // the glass header and the "My Devices" rest position clear it explicitly.
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(modifier = modifier.fillMaxSize()) {
            // ===== Backdrop source: the scrolling content the glass samples.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
            ) {
                // Background drawn into the backdrop so the layer is never empty
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )

                // Ambient purple/violet/pink haze drifting behind all content
                AmbientSmokeBackground(modifier = Modifier.fillMaxSize())

                    val discoveredDevicesList = discoveredDevices
                    val deviceConfig: DeviceConfig = koinInject()

                    val (trustedLocal, untrustedDevices) = discoveredDevicesList.partition { device ->
                        (AuthState.pairedFingerprints.contains(device.info.fingerprint) ||
                                (device.info.identityHash != null && device.info.identityHash == deviceConfig.identityHash))
                    }

                    // Consolidated and prioritized: Real Trusted Devices (Active Transfer > Recency)
                    val search = com.dexstudios.dex.ui.state.TopAppBarState.searchQuery
                    val consolidatedTrusted = remember(trustedLocal, rosterDevices, uploadState.targetFingerprint, downloadState.sourceFingerprint, search) {
                        val map = mutableMapOf<String, DiscoveredDevice>()
                        // WAN devices baseline
                        rosterDevices.forEach { map[it.info.fingerprint] = it }
                        // Local trusted devices overwrite roster (LAN is preferred/faster)
                        trustedLocal.forEach { map[it.info.fingerprint] = it }

                        map.values.filter { it.info.alias.contains(search, ignoreCase = true) }
                            .sortedWith(
                                compareByDescending<DiscoveredDevice> {
                                    (it.info.fingerprint == uploadState.targetFingerprint || it.info.fingerprint == downloadState.sourceFingerprint)
                                }.thenByDescending { AuthState.pairedTimes[it.info.fingerprint] ?: 0L }
                                    .thenByDescending { it.lastSeenTimestamp }
                            ).toList()
                    }

                    val filteredUntrusted = remember(untrustedDevices, search) {
                        untrustedDevices.filter { it.info.alias.contains(search, ignoreCase = true) }
                    }

                    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
                        MainScreenCompact(
                            listState = listState,
                            consolidatedTrusted = consolidatedTrusted,
                            untrustedDevices = filteredUntrusted,
                            search = search,
                            showHelpHint = showHelpHint,
                            onTrustedDeviceButtonClick = { device ->
                                if (device.viaRoster) {
                                    selectedRosterDevice = device
                                } else {
                                    selectedDevice = device
                                }
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            onUntrustedDeviceButtonClick = { device ->
                                connectOptionsDevice = device
                            },
                            onDeviceLongClick = { device ->
                                contextMenuDevice = device
                            },
                            onScanClick = { launchQrScanner() },
                            statusBarHeight = statusBarHeight
                        )
                    } else {
                        MainScreenGrid(
                            consolidatedTrusted = consolidatedTrusted,
                            untrustedDevices = filteredUntrusted,
                            search = search,
                            showHelpHint = showHelpHint,
                            onTrustedDeviceButtonClick = { device ->
                                if (device.viaRoster) {
                                    selectedRosterDevice = device
                                } else {
                                    selectedDevice = device
                                }
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            onUntrustedDeviceButtonClick = { device ->
                                connectOptionsDevice = device
                            },
                            onDeviceLongClick = { device ->
                                contextMenuDevice = device
                            },
                            onScanClick = { launchQrScanner() },
                            statusBarHeight = statusBarHeight
                        )
                    }
            }

            // ===== Glass overlays: drawn AFTER the captured content, sample it =====
            GlassScrollEdge(
                backdrop = contentBackdrop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeight + 64.dp)
            )
        }
    }

    if (uploadState.error != null) {
        NetworkErrorDialog(
            error = stringResource(R.string.upload_failed, humanizeTransferError(uploadState.error ?: "")),
            onDismiss = { viewModel.clientEngine.resetUploadState() }
        )
    }

    if (downloadState.error != null) {
        NetworkErrorDialog(
            error = stringResource(R.string.download_failed, humanizeTransferError(downloadState.error ?: "")),
            onDismiss = { com.dexstudios.dex.network.TcpDownloadService.resetDownloadState() }
        )
    }

    if (showTroubleshootDialog) {
        AlertDialog(
            onDismissRequest = { showTroubleshootDialog = false },
            title = { Text(text = "Troubleshooting") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Ensure both devices are powered on.")
                    Text("2. Check that both devices are connected to the same Wi-Fi network.")
                    Text("3. Try restarting the app on both devices.")
                }
            },
            confirmButton = {
                DeXTextButton(onClick = { showTroubleshootDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    contextMenuDevice?.let { device ->
        val isTrusted = AuthState.pairedFingerprints.contains(device.info.fingerprint)
        DeviceContextMenu(
            device = device,
            isTrusted = isTrusted,
            backdrop = contentBackdrop,
            onSendFile = {
                selectedDevice = device
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            onPair = {
                if (pairingDeviceFingerprint != device.info.fingerprint) {
                    pairingDeviceFingerprint = device.info.fingerprint
                    Toast.makeText(context, context.getString(R.string.pairing_with, device.info.alias), Toast.LENGTH_SHORT).show()
                    viewModel.requestPairing(device) { success ->
                        pairingDeviceFingerprint = null
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.pairing_request_sent, device.info.alias), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.pairing_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onForget = {
                viewModel.requestUnpair(device) { success ->
                    // Request fired off, PC should unpair shortly if online
                }
                DeviceManager.removePairedFingerprint(device.info.fingerprint)
                Toast.makeText(context, "Forgotten ${device.info.alias}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { contextMenuDevice = null }
        )
    }

    connectOptionsDevice?.let { device ->
        ConnectionOptionsDialog(
            device = device,
            backdrop = contentBackdrop,
            onPinCode = {
                // Original pairing logic
                if (AuthState.incomingPairRequest.value != null) return@ConnectionOptionsDialog
                if (pairingDeviceFingerprint == device.info.fingerprint) return@ConnectionOptionsDialog
                pairingDeviceFingerprint = device.info.fingerprint
                Toast.makeText(context, context.getString(R.string.pairing_with, device.info.alias), Toast.LENGTH_SHORT).show()
                viewModel.requestPairing(device) { success ->
                    pairingDeviceFingerprint = null
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.pairing_request_sent, device.info.alias), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.pairing_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onQrCode = { launchQrScanner() },
            onDismiss = { connectOptionsDevice = null }
        )
    }
}

private fun humanizeTransferError(raw: String): String = when {
    raw.contains("HTTP 404", ignoreCase = true) ||
        raw.contains("HTTP 403", ignoreCase = true) ||
        raw.contains("HTTP 410", ignoreCase = true) -> "Transfer link expired"
    raw.contains("no connection to PC", ignoreCase = true) ||
        raw.contains("ConnectException", ignoreCase = true) ||
        raw.contains("SocketTimeoutException", ignoreCase = true) ||
        raw.contains("Failed to connect", ignoreCase = true) ||
        raw.contains("Cronet", ignoreCase = true) -> "Cannot connect to device"
    raw.contains("Cannot write to Downloads/DeX", ignoreCase = true) -> "Permission error on storage"
    raw.contains("cancelled", ignoreCase = true) -> "Transfer cancelled"
    raw.contains("Upload failed for all files", ignoreCase = true) -> "Upload failed"
    else -> raw
}
