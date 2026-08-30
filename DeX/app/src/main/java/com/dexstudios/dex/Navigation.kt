package com.dexstudios.dex
import timber.log.Timber

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.dexstudios.dex.ui.icons.MaterialSymbols
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dexstudios.dex.network.AuthState
import com.dexstudios.dex.network.DeXPorts
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.network.MessageHandler
import com.dexstudios.dex.network.RegisterDto
import com.dexstudios.dex.network.TransferWorkKeys
import com.dexstudios.dex.ui.components.ConnectionOptionsDialog
import com.dexstudios.dex.ui.components.FloatingTopAppBar
import com.dexstudios.dex.ui.components.LiquidGlassButton
import com.dexstudios.dex.ui.components.NavBottomSheet
import com.dexstudios.dex.ui.components.PairingRequestDialog
import com.dexstudios.dex.ui.components.SheetExpandedMode
import com.dexstudios.dex.ui.components.SheetTier
import com.dexstudios.dex.ui.history.HistoryScreen
import com.dexstudios.dex.ui.main.MainScreen
import com.dexstudios.dex.ui.main.MainScreenUiState
import com.dexstudios.dex.ui.main.MainScreenViewModel
import com.dexstudios.dex.ui.main.components.DeviceCarousel
import com.dexstudios.dex.ui.main.components.MediaPickerTray
import com.dexstudios.dex.ui.state.TopAppBarState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun MainNavigation(
    windowSizeClass: WindowSizeClass,
    onDismiss: () -> Unit = {}
) {
    val messageHandler: MessageHandler = koinInject()
    val viewModel: MainScreenViewModel = koinViewModel()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val discoveredDevices = (uiState as? MainScreenUiState.Success)?.data ?: emptyList()
    val uploadState by viewModel.clientEngine.uploadState.collectAsStateWithLifecycle()
    val downloadState by com.dexstudios.dex.network.TcpDownloadService.downloadState.collectAsStateWithLifecycle()

    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var showPairingModal by remember { mutableStateOf(false) }
    var activeExpandedMode by remember { mutableStateOf(SheetExpandedMode.Media) }

    val mainListState = rememberLazyListState()
    val historyListState = rememberLazyListState()

    val context = LocalContext.current
    val resources = LocalResources.current

    // Onboarding state hoisted so both the sheet content and the overlay below can react to it
    val onboardingPrefs = remember { context.getSharedPreferences("dex_onboarding", android.content.Context.MODE_PRIVATE) }
    var showOnboarding by remember { mutableStateOf(!onboardingPrefs.getBoolean("onboarding_done", false)) }

    LaunchedEffect(showOnboarding) {
        TopAppBarState.isOnboardingVisible = showOnboarding
    }

    // QR Code Scanner Launcher
    val launchQrScanner: () -> Unit = {
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
                        Toast.makeText(context, "Pairing via QR with ${allIps.first()}", Toast.LENGTH_SHORT).show()
                        allIps.forEach { ip ->
                            viewModel.discoveryEngine.sendManualDiscovery(ip, port)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // File Send Dispatcher
    val sendFilesToTarget: (DiscoveredDevice, List<Uri>) -> Unit = { target, uris ->
        if (uris.isNotEmpty()) {
            Toast.makeText(
                context,
                "Sending ${uris.size} item(s) to ${target.info.alias.ifEmpty { target.info.deviceModel }}",
                Toast.LENGTH_SHORT
            ).show()

            viewModel.clientEngine.resetUploadState()
            val urisJson = try {
                Json.encodeToString(uris.map { it.toString() })
            } catch (e: Exception) {
                Timber.e(e, "Operation failed")
                ""
            }

            if (urisJson.isNotEmpty()) {
                val inputData = workDataOf(
                    TransferWorkKeys.IP to target.ip,
                    TransferWorkKeys.PORT to target.info.port,
                    TransferWorkKeys.URIS to urisJson,
                    TransferWorkKeys.TARGET_FINGERPRINT to target.info.fingerprint,
                    TransferWorkKeys.TARGET_ALIAS to target.info.alias
                )

                val workRequest = OneTimeWorkRequestBuilder<com.dexstudios.dex.network.UploadWorker>()
                    .setInputData(inputData)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

                viewModel.clientEngine.activeWorkId = workRequest.id
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val contentBackdrop = rememberLayerBackdrop()
        val incomingPairRequest by AuthState.incomingPairRequest.collectAsStateWithLifecycle()

        // Preview Mock Devices for visual tuning & testing carousel without physical peers
        val previewMockDevices = remember {
            listOf(
                DiscoveredDevice(
                    ip = "192.168.1.101",
                    info = RegisterDto(
                        alias = "MacBook Pro 16\"",
                        version = "1.0",
                        deviceModel = "MacBookPro18,1",
                        deviceType = "laptop",
                        fingerprint = "mock_macbook_pro",
                        port = DeXPorts.HTTPS,
                        protocol = "wss",
                        download = true
                    )
                ),
                DiscoveredDevice(
                    ip = "192.168.1.102",
                    info = RegisterDto(
                        alias = "Apple Watch Ultra",
                        version = "1.0",
                        deviceModel = "Watch Ultra",
                        deviceType = "watch",
                        fingerprint = "mock_watch_ultra",
                        port = DeXPorts.HTTPS,
                        protocol = "wss",
                        download = true
                    )
                ),
                DiscoveredDevice(
                    ip = "192.168.1.103",
                    info = RegisterDto(
                        alias = "iPad Air",
                        version = "1.0",
                        deviceModel = "iPad13,1",
                        deviceType = "tablet",
                        fingerprint = "mock_ipad_air",
                        port = DeXPorts.HTTPS,
                        protocol = "wss",
                        download = true
                    )
                ),
                DiscoveredDevice(
                    ip = "192.168.1.104",
                    info = RegisterDto(
                        alias = "Galaxy S24 Ultra",
                        version = "1.0",
                        deviceModel = "SM-S928B",
                        deviceType = "phone",
                        fingerprint = "mock_galaxy_phone",
                        port = DeXPorts.HTTPS,
                        protocol = "wss",
                        download = true
                    )
                )
            )
        }
        var showPreviewDevices by remember { mutableStateOf(false) }
        val effectiveDevices = if (showPreviewDevices) previewMockDevices else discoveredDevices

        val isDimmed by remember {
            derivedStateOf {
                TopAppBarState.isProfileExpanded || TopAppBarState.isSearchExpanded
            }
        }
        val globalDimAlpha by animateFloatAsState(
            targetValue = if (isDimmed) 0.75f else 0f,
            animationSpec = tween(500),
            label = "globalDimAlpha"
        )

        // Predictive back gesture handling for expanded overlays (profile/search)
        PredictiveBackHandler(enabled = TopAppBarState.isProfileExpanded || TopAppBarState.isSearchExpanded) { progressFlow ->
            try {
                progressFlow.collect { /* progress */ }
                TopAppBarState.isProfileExpanded = false
                TopAppBarState.isSearchExpanded = false
            } catch (_: kotlin.coroutines.cancellation.CancellationException) {
                // Cancelled
            }
        }

        // ===== 3-Tier Dynamic Bottom Sheet Engine (50%, 80%, 100%) =====
        // Keep the main nav sheet dormant while the onboarding sheet owns the screen
        if (!showOnboarding) {
        NavBottomSheet(
            backdrop = contentBackdrop,
            initialTier = SheetTier.Half,
            onDismiss = onDismiss,
            sheetContent = { expansionFraction, currentTier, halfHeightDp, expandTo, collapseToHalf ->
                // Automatically reset mode to Media when collapsed to 50%
                LaunchedEffect(expansionFraction) {
                    if (expansionFraction <= 0.05f) {
                        activeExpandedMode = SheetExpandedMode.Media
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // --- Resting 50% Content: Constrained to halfHeightDp so bottom button is pinned at bottom of 50% sheet ---
                    if (expansionFraction < 0.35f) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(halfHeightDp)
                                .graphicsLayer {
                                    alpha = (1f - (expansionFraction / 0.25f)).coerceIn(0f, 1f)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 1. Centered Device Carousel / Morph Empty State
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                DeviceCarousel(
                                    devices = effectiveDevices,
                                    selectedDevice = selectedDevice ?: effectiveDevices.firstOrNull(),
                                    backdrop = contentBackdrop,
                                    onDeviceSelect = { selectedDevice = it },
                                    onDeviceLongClick = { selectedDevice = it; showPairingModal = true },
                                    onAddDeviceClick = { showPairingModal = true },
                                    uploadState = uploadState,
                                    downloadState = downloadState
                                )
                            }

                            // 2. Pinned Bottom Action Button & Preview Devices Toggle (Deposit Style)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SheetActionButton(
                                    text = if (effectiveDevices.isNotEmpty()) "Send File" else "Pair Device",
                                    onClick = {
                                        if (effectiveDevices.isNotEmpty()) {
                                            activeExpandedMode = SheetExpandedMode.Media
                                            expandTo(SheetTier.High)
                                        } else {
                                            showPairingModal = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                val isDark = isSystemInDarkTheme()
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (showPreviewDevices) MaterialTheme.colorScheme.primary
                                            else if (isDark) Color.White.copy(alpha = 0.12f)
                                            else Color.Black.copy(alpha = 0.08f)
                                        )
                                        .clickable {
                                            showPreviewDevices = !showPreviewDevices
                                            if (showPreviewDevices && selectedDevice == null) {
                                                selectedDevice = previewMockDevices.first()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Devices,
                                        contentDescription = "Toggle Preview Connected Devices",
                                        tint = if (showPreviewDevices) MaterialTheme.colorScheme.onPrimary else if (isDark) Color.White else Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // --- Expanded Content: 80% Media Hub & 100% Immersion with [Media | History] Segmented Control ---
                    if (expansionFraction >= 0.15f) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = ((expansionFraction - 0.15f) / 0.35f).coerceIn(0f, 1f)
                                }
                        ) {
                            // 2-Option Segmented Pill Control [ Media | History ] right under drag handle
                            SheetSegmentedControl(
                                selectedMode = activeExpandedMode,
                                onSelectMode = { activeExpandedMode = it },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Active Body View (Media Hub vs Transfer History)
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (activeExpandedMode == SheetExpandedMode.Media) {
                                    MediaPickerTray(
                                        backdrop = contentBackdrop,
                                        onSend = { uris ->
                                            val target = selectedDevice ?: discoveredDevices.firstOrNull()
                                            if (target != null) {
                                                sendFilesToTarget(target, uris)
                                            }
                                            collapseToHalf()
                                        },
                                        onClose = { collapseToHalf() }
                                    )
                                } else {
                                    HistoryScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        listState = historyListState
                                    )
                                }
                            }
                        }
                    }
                }
            },
            content = { expansionFraction, paddingValues ->
                // Main content: Screen behind the sheet
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(contentBackdrop)
                ) {
                    MainScreen(
                        modifier = Modifier.graphicsLayer {
                            val scale = 1f - (expansionFraction * 0.05f)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - (expansionFraction * 0.3f)
                        },
                        listState = mainListState,
                        windowSizeClass = windowSizeClass
                    )
                }
            }
        )
        }

        // Dimming overlay for top bar expansions (profile/search)
        if (isDimmed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .graphicsLayer { alpha = globalDimAlpha }
                    .background(MaterialTheme.colorScheme.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            TopAppBarState.isProfileExpanded = false
                            TopAppBarState.isSearchExpanded = false
                        }
                    )
            )
        }

        // Floating Top App Bar (logo, profile island, search island)
        // Hidden while the onboarding sheet owns the screen
        if (!showOnboarding) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.zIndex(1f)
        ) {
            FloatingTopAppBar(
                backdrop = contentBackdrop
            )
        }
        }

        // Pair Request Prompt
        incomingPairRequest?.let { req ->
            com.dexstudios.dex.ui.components.PairingRequestDialog(
                alias = req.alias,
                expectedPin = req.pin,
                onAccept = { enteredPin: String ->
                    req.deferred.complete(enteredPin)
                },
                onFinished = {
                    AuthState.incomingPairRequest.value = null
                    Toast.makeText(context, resources.getString(R.string.paired_successfully), Toast.LENGTH_SHORT).show()
                },
                onReject = {
                    req.deferred.complete("")
                    AuthState.incomingPairRequest.value = null
                },
                deadlineElapsedMs = req.deadlineElapsedMs,
                onDigitEntered = { count: Int ->
                    messageHandler.sendPinDigitEntered(count)
                },
                modifier = Modifier.zIndex(100f)
            )
        }

        if (showOnboarding) {
            com.dexstudios.dex.ui.components.OnboardingSheet(
                onDismiss = { showOnboarding = false },
                modifier = Modifier.zIndex(100f)
            )
        }

        // Pairing Options Dialog (PIN Code / Scan QR)
        if (showPairingModal) {
            val targetDevice = selectedDevice ?: discoveredDevices.firstOrNull() ?: DiscoveredDevice(
                ip = "0.0.0.0",
                info = RegisterDto(
                    alias = "Nearby Device",
                    version = "1.0",
                    deviceModel = "DeX Target",
                    deviceType = "laptop",
                    fingerprint = "",
                    port = DeXPorts.HTTPS,
                    protocol = "wss",
                    download = true
                )
            )

            ConnectionOptionsDialog(
                device = targetDevice,
                backdrop = contentBackdrop,
                onPinCode = {
                    showPairingModal = false
                    viewModel.requestPairing(targetDevice) { ok ->
                        if (!ok) {
                            Toast.makeText(context, "Pairing request failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onQrCode = {
                    showPairingModal = false
                    launchQrScanner()
                },
                onDismiss = {
                    showPairingModal = false
                }
            )
        }
    }
}

/**
 * 2-Option Segmented Pill Control (Media | History) at 100% Full Tier.
 */
@Composable
private fun SheetSegmentedControl(
    selectedMode: SheetExpandedMode,
    onSelectMode: (SheetExpandedMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .padding(horizontal = 32.dp, vertical = 6.dp)
            .height(38.dp)
            .clip(pillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media Tab
            val isMedia = selectedMode == SheetExpandedMode.Media
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .clickable { onSelectMode(SheetExpandedMode.Media) }
                    .background(if (isMedia) MaterialTheme.colorScheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Media",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isMedia) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (isMedia) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // History Tab
            val isHistory = selectedMode == SheetExpandedMode.History
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .clickable { onSelectMode(SheetExpandedMode.History) }
                    .background(if (isHistory) MaterialTheme.colorScheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isHistory) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (isHistory) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Modern High-Contrast Pill Action Button (Deposit-style CTA at the bottom of the sheet).
 */
@Composable
private fun SheetActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val buttonBgColor = if (isDark) Color.White else Color.Black
    val buttonTextColor = if (isDark) Color.Black else Color.White
    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(pillShape)
            .background(buttonBgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = buttonTextColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}

