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
import com.dexstudios.dex.ui.components.TransferProgressOverlay
import com.dexstudios.dex.ui.components.FloatingTopAppBar
import com.dexstudios.dex.ui.components.*
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            TransferProgressOverlay(
                downloadState = downloadState,
                uploadState = uploadState,
                backdrop = contentBackdrop,
                onCancelDownload = { TcpDownloadService.cancelDownload(context) },
                onCancelUpload = { viewModel.clientEngine.cancelUpload(context) }
            )
        }
    ) { _ ->
        // Status bar height — content scrolls behind the native status bar, so
        // the glass header and the "My Devices" rest position clear it explicitly.
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

                val discoveredDevicesList = discoveredDevices
                val deviceConfig: DeviceConfig = koinInject()

                val (trustedLocal, untrustedDevices) = discoveredDevicesList.partition { device ->
                    (AuthState.pairedFingerprints.contains(device.info.fingerprint) ||
                        (device.info.identityHash != null && device.info.identityHash == deviceConfig.identityHash))
                }

                // Consolidated and prioritized: Real Trusted Devices (Active Transfer > Recency)
                val consolidatedTrusted = remember(trustedLocal, rosterDevices, uploadState.targetFingerprint, downloadState.sourceFingerprint) {
                    val map = mutableMapOf<String, DiscoveredDevice>()
                    // WAN devices baseline
                    rosterDevices.forEach { map[it.info.fingerprint] = it }
                    // Local trusted devices overwrite roster (LAN is preferred/faster)
                    trustedLocal.forEach { map[it.info.fingerprint] = it }

                    map.values.sortedWith(
                        compareByDescending<DiscoveredDevice> {
                            (it.info.fingerprint == uploadState.targetFingerprint || it.info.fingerprint == downloadState.sourceFingerprint)
                        }.thenByDescending { AuthState.pairedTimes[it.info.fingerprint] ?: 0L }
                         .thenByDescending { it.lastSeenTimestamp }
                    ).toList()
                }

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
                    // 1. "My Devices" Section (Horizontal Carousel)
                    item {
                        // Top padding rests the "My Devices" title visibly right under
                        // the glass avatar button (which clears the status bar);
                        // it still scrolls up beneath the header like the rest.
                        Column(modifier = Modifier.padding(top = statusBarHeight + 64.dp, bottom = 8.dp)) {
                            Text(
                                text = "My Devices",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // A. Real Trusted Devices (Sorted)
                                items(consolidatedTrusted, key = { it.info.fingerprint }) { device ->
                                    DeviceListItem(
                                        modifier = Modifier.width(300.dp),
                                        device = device,
                                        isTrusted = true,
                                        onClick = {},
                                        onButtonClick = {
                                            if (device.viaRoster) {
                                                selectedRosterDevice = device
                                            } else {
                                                selectedDevice = device
                                            }
                                            filePickerLauncher.launch(arrayOf("*/*"))
                                        },
                                        onLongClick = {
                                            contextMenuDevice = device
                                        }
                                    )
                                }

                                // B. Dummy Devices (Placeholders at the end)
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
                    item {
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            Text(
                                text = "Discovered",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // A. Real Discovered Devices (Untrusted)
                                items(untrustedDevices, key = { it.info.fingerprint }) { device ->
                                    DeviceListItem(
                                        modifier = Modifier.width(300.dp),
                                        device = device,
                                        isTrusted = false,
                                        onClick = {},
                                        onButtonClick = {
                                            // Show connection options (PIN vs QR)
                                            connectOptionsDevice = device
                                        },
                                        onLongClick = {
                                            // Long-press opens the device context menu
                                            contextMenuDevice = device
                                        }
                                    )
                                }

                                // B. Permanent "Scan to add Device" Card
                                item {
                                    ScanToAddDeviceCard(
                                        showHelpHint = showHelpHint,
                                        onScanClick = { launchQrScanner() }
                                    )
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
private fun ScanToAddDeviceCard(
    showHelpHint: Boolean,
    onScanClick: () -> Unit
) {
    var showHelpContent by remember { mutableStateOf(false) }

    val cardShape = RoundedCornerShape(48.dp)
    DeXPanel(
        shape = cardShape,
        modifier = Modifier
            .width(300.dp)
            .height(340.dp)
            .bubbleFluidity(targetScale = 0.97f, pullFactor = 0.05f)
    ) {
        AnimatedContent(
            targetState = showHelpContent,
            transitionSpec = {
                (fadeIn(tween(400)) + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut(tween(400)) + scaleOut(targetScale = 0.95f))
            },
            label = "scan_card_content"
        ) { isHelp ->
            if (isHelp) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.discovery_help_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DiscoveryHelpStep(number = "1", text = stringResource(R.string.discovery_help_step1))
                        DiscoveryHelpStep(number = "2", text = stringResource(R.string.discovery_help_step2))
                        DiscoveryHelpStep(number = "3", text = stringResource(R.string.discovery_help_step3))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    DeXButton(
                        onClick = { showHelpContent = false },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = CircleShape
                    ) {
                        Text(stringResource(R.string.discovery_help_close), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon centered in the top area
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_qr_code_scanner),
                                contentDescription = "Scan",
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "Scan to add Device",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = showHelpHint) { showHelpContent = true },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = showHelpHint,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "hint_text"
                        ) { hintActive ->
                            if (hintActive) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = com.dexstudios.dex.ui.icons.MaterialSymbols.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.discovery_help_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Text(
                                    text = "QRCode must be triggered from PC",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    DeXButton(
                        onClick = onScanClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text("Scan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryHelpStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
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
