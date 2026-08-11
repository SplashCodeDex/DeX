package com.dexstudios.dex.ui.main

import android.net.Uri
import androidx.core.net.toUri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.dexstudios.dex.network.RegisterDto
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
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
import com.dexstudios.dex.network.DiscoveredDevice
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
import com.dexstudios.dex.ui.components.TransferProgressOverlay
import com.dexstudios.dex.ui.components.FloatingTopAppBar
import com.dexstudios.dex.ui.components.*
import com.dexstudios.dex.ui.components.glass.GlassEdge
import com.dexstudios.dex.ui.components.glass.GlassScrollEdge
import androidx.compose.ui.text.style.TextAlign
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@android.annotation.SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    viewModel: MainScreenViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wsService: WebSocketClientService = koinInject()
                    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
                    var selectedRosterDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var pairingDeviceFingerprint by remember { mutableStateOf<String?>(null) }
    var showTroubleshootDialog by remember { mutableStateOf(false) }
    var contextMenuDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var connectOptionsDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

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
                    val ip = uri.host
                    if (ip != null) {
                        Toast.makeText(context, context.getString(R.string.toast_scanned_ip, ip), Toast.LENGTH_SHORT).show()
                        viewModel.discoveryEngine.sendManualDiscovery(ip)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, context.getString(R.string.toast_scan_failed, e.message.toString()), Toast.LENGTH_SHORT).show()
            }
    }

    // Modern Android Photo/File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
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
                "targetFingerprint" to device.info.fingerprint
            ).let { base ->
                val identityHash = device.info.identityHash
                val googleSub = device.info.googleSub
                if (identityHash != null || googleSub != null) {
                    androidx.work.Data.Builder().putAll(base)
                        .apply {
                            if (identityHash != null) putString("targetIdentityHash", identityHash)
                            if (googleSub != null) putString("targetGoogleSub", googleSub)
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

    // Folder bundles: long-press a device to pick a folder, sent without zipping
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val rosterTarget = selectedRosterDevice
        if (rosterTarget != null) {
            selectedRosterDevice = null
            val punchRequest = OneTimeWorkRequestBuilder<com.dexstudios.dex.network.PunchSendWorker>()
                .setInputData(
                    workDataOf(
                        "targetFingerprint" to rosterTarget.info.fingerprint,
                        "uris" to "[]",
                        "folderTreeUri" to uri.toString()
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(punchRequest)
            return@rememberLauncherForActivityResult
        }
        selectedDevice?.let { device ->
            val folderRequest = OneTimeWorkRequestBuilder<com.dexstudios.dex.network.UploadWorker>()
                .setInputData(
                    workDataOf(
                        "ip" to device.ip,
                        "port" to device.info.port,
                        "uris" to "[]",
                        "targetFingerprint" to device.info.fingerprint,
                        "folderTreeUri" to uri.toString()
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            viewModel.clientEngine.activeWorkId = folderRequest.id
            WorkManager.getInstance(context).enqueue(folderRequest)
        }
    }

    // First-run onboarding: shown once, dismissed via a persisted flag
    val onboardingPrefs = remember { context.getSharedPreferences("dex_onboarding", android.content.Context.MODE_PRIVATE) }
    var showOnboarding by remember { mutableStateOf(!onboardingPrefs.getBoolean("onboarding_done", false)) }
    val contentBackdrop = rememberLayerBackdrop()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Full-window content: the scrolling content passes behind the native
        // status bar and navigation bar — the glass edge fades keep it readable.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val downloadState by com.dexstudios.dex.network.TcpDownloadService.downloadState.collectAsStateWithLifecycle()
            val uploadState by viewModel.clientEngine.uploadState.collectAsStateWithLifecycle()
            TransferProgressOverlay(
                downloadState = downloadState,
                uploadState = uploadState,
                backdrop = contentBackdrop,
                onCancelDownload = { com.dexstudios.dex.network.TcpDownloadService.cancelDownload(context) },
                onCancelUpload = { viewModel.clientEngine.cancelUpload(context) }
            )
        }
    ) { padding ->
        val devices = (uiState as? MainScreenUiState.Success)?.data ?: emptyList()
        val rosterDevices by com.dexstudios.dex.network.PunchState.devices.collectAsStateWithLifecycle()
        // Status bar height — content scrolls behind the native status bar, so
        // the glass header and the "Recent" rest position clear it explicitly.
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(modifier = modifier.fillMaxSize()) {
            // ===== Backdrop source: the scrolling content the glass samples.
            // Glass elements (top bar below) are drawn OUTSIDE this subtree —
            // a backdrop that captures the glass sampling it is a render loop
            // and crashes the renderer (documented library pitfall).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The root already applies safeDrawingPadding — drop the
                    // Scaffold's duplicate system-bar insets so the content sits
                    // flush with the status bar line (top) and reaches the
                    // navbar line (bottom) with no gaps.
                    .layerBackdrop(contentBackdrop)
            ) {
                // Background drawn into the backdrop so the layer is never empty
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )

                val discoveredDevices = (uiState as? MainScreenUiState.Success)?.data ?: emptyList()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Content scrolls edge-to-edge — behind the native status bar
                    // (top) and the native nav bar (bottom); the edge fades keep it
                    // readable. The last card rests exactly at the floating
                    // navbar's top line (72dp + 16dp margin from the screen bottom).
                    contentPadding = PaddingValues(
                        top = 0.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. "Recent" Section (Horizontal Carousel)
                    item {
                        // Top padding rests the "Recent" title visibly right under
                        // the glass avatar button (which clears the status bar);
                        // it still scrolls up beneath the header like the rest.
                        Column(modifier = Modifier.padding(top = statusBarHeight + 64.dp, bottom = 8.dp)) {
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    DummyDeviceCard(
                                        alias = "Gaming PC",
                                        model = "Custom Build (RTX 4090)",
                                        wallpaper = R.drawable.wallpaper_gaming
                                    )
                                }
                                item {
                                    DummyDeviceCard(
                                        alias = "Home Server",
                                        model = "TrueNAS Core",
                                        wallpaper = R.drawable.wallpaper_server
                                    )
                                }
                                item {
                                    DummyDeviceCard(
                                        alias = "Work Laptop",
                                        model = "MacBook Pro M3",
                                        wallpaper = R.drawable.wallpaper_laptop
                                    )
                                }
                            }
                        }
                    }

                    // 2. "Discovered" Section Title
                    if (discoveredDevices.isNotEmpty()) {
                        item {
                            Text(
                                text = "Discovered",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // 3. Real Discovered Devices
                    items(discoveredDevices, key = { it.info.fingerprint }) { device ->
                        val deviceConfig: com.dexstudios.dex.network.DeviceConfig = koinInject()
                        val isTrusted = AuthState.pairedFingerprints.contains(device.info.fingerprint) ||
                            (device.info.identityHash != null && device.info.identityHash == deviceConfig.identityHash)

                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            DeviceListItem(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .width(300.dp), // Narrower width as requested
                                device = device,
                                isTrusted = isTrusted,
                                onClick = {
                                    if (isTrusted) {
                                        selectedDevice = device
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    }
                                    // If not trusted, tapping the card does nothing
                                },
                                onButtonClick = {
                                    if (isTrusted) {
                                        selectedDevice = device
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    } else {
                                        // Show connection options (PIN vs QR)
                                        connectOptionsDevice = device
                                    }
                                },
                                onLongClick = {
                                    // Long-press opens the device context menu
                                    contextMenuDevice = device
                                }
                            )
                        }
                    }

                    // 3.5 My devices over WAN (same email, direct punch transfers via the PC)
                    if (rosterDevices.isNotEmpty()) {
                        item {
                            Text(
                                text = "My Devices",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        items(rosterDevices, key = { "roster-${it.info.fingerprint}" }) { device ->
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                DeviceListItem(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .width(300.dp),
                                    device = device,
                                    isTrusted = true,
                                    onClick = {
                                        selectedRosterDevice = device
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    onLongClick = {
                                        // Long-press opens the device context menu
                                        contextMenuDevice = device
                                    },
                                )
                            }
                        }
                    }

                    // 4. Empty State Panel
                    if (discoveredDevices.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                DeXPanel(
                                    shape = RoundedCornerShape(32.dp),
                                    modifier = Modifier
                                        .widthIn(max = 400.dp)
                                        .fillMaxWidth()
                                        .bubbleFluidity(targetScale = 0.97f, pullFactor = 0.05f)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(bottom = 24.dp)
                                                .size(96.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = ImageVector.vectorResource(R.drawable.ic_qr_code_scanner),
                                                contentDescription = "Scan",
                                                modifier = Modifier.size(48.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Text(
                                            text = "Scan to add Device",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        Text(
                                            text = "No Devices Connected. Make sure they are powered on and nearby.",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
                                            textAlign = TextAlign.Center
                                        )

                                        DeXButton(
                                            onClick = { launchQrScanner() },
                                            modifier = Modifier.fillMaxWidth(0.8f).height(44.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Text("Scan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

                // ===== Glass overlays: drawn AFTER the captured content, sample it =====
                // Frosted edge fade — content progressively blurs as it approaches
                // the native status bar / glass header (top).
                // The bottom edge is covered by the glass nav bar already — no
                // separate bottom fade needed.
                GlassScrollEdge(
                    backdrop = contentBackdrop,
                    edge = GlassEdge.Top,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(statusBarHeight + 64.dp)
                )


        }
    }

    val uploadState by viewModel.clientEngine.uploadState.collectAsStateWithLifecycle()
    if (uploadState.error != null) {
        NetworkErrorDialog(
            error = stringResource(R.string.upload_failed, humanizeTransferError(uploadState.error ?: "")),
            onDismiss = { viewModel.clientEngine.resetUploadState() }
        )
    }

    val downloadState by com.dexstudios.dex.network.TcpDownloadService.downloadState.collectAsStateWithLifecycle()
    if (downloadState.error != null) {
        NetworkErrorDialog(
            error = stringResource(R.string.download_failed, humanizeTransferError(downloadState.error ?: "")),
            onDismiss = { com.dexstudios.dex.network.TcpDownloadService.resetDownloadState() }
        )
    }

    if (showOnboarding) {
        OnboardingDialog(
            onDismiss = {
                onboardingPrefs.edit { putBoolean("onboarding_done", true) }
                showOnboarding = false
            }
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

@Composable
private fun DummyDeviceCard(alias: String, model: String, wallpaper: Any) {
    val dummyDevice = remember(alias, model) {
        DiscoveredDevice(
            ip = "0.0.0.0",
            info = RegisterDto(
                alias = alias,
                version = "1.0",
                deviceModel = model,
                deviceType = "pc",
                fingerprint = alias,
                port = 0,
                protocol = "https",
                download = true
            )
        )
    }
    DeviceListItem(
        device = dummyDevice,
        onClick = {}, // Do nothing as requested
        modifier = Modifier.width(300.dp),
        isTrusted = true,
        wallpaper = wallpaper
    )
}

@Composable
private fun humanizeTransferError(raw: String): String = when {
    raw.contains("HTTP 404", ignoreCase = true) ||
        raw.contains("HTTP 403", ignoreCase = true) ||
        raw.contains("HTTP 410", ignoreCase = true) -> stringResource(R.string.error_transfer_expired)
    raw.contains("no connection to PC", ignoreCase = true) ||
        raw.contains("ConnectException", ignoreCase = true) ||
        raw.contains("SocketTimeoutException", ignoreCase = true) ||
        raw.contains("Failed to connect", ignoreCase = true) ||
        raw.contains("Cronet", ignoreCase = true) -> stringResource(R.string.error_transfer_no_connection)
    raw.contains("Cannot write to Downloads/DeX", ignoreCase = true) -> stringResource(R.string.error_transfer_folder)
    raw.contains("cancelled", ignoreCase = true) -> stringResource(R.string.error_transfer_cancelled)
    raw.contains("Upload failed for all files", ignoreCase = true) -> stringResource(R.string.error_upload_all)
    else -> raw
}
