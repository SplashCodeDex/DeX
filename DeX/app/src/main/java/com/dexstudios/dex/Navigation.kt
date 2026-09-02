package com.dexstudios.dex

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dexstudios.dex.network.*
import com.dexstudios.dex.ui.components.*
import com.dexstudios.dex.ui.components.glass.*
import com.dexstudios.dex.ui.history.HistoryScreen
import com.dexstudios.dex.ui.main.MainScreen
import com.dexstudios.dex.ui.main.MainScreenUiState
import com.dexstudios.dex.ui.main.MainScreenViewModel
import com.dexstudios.dex.ui.main.components.DeviceCarousel
import com.dexstudios.dex.ui.main.components.MediaPickerTray
import com.dexstudios.dex.ui.main.components.MediaTrayTab
import com.dexstudios.dex.ui.state.TopAppBarState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.dexstudios.dex.ui.icons.MaterialSymbols
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber

enum class BottomRightButtonMode {
    Devices,
    Profile,
    History
}

fun BottomRightButtonMode.next(): BottomRightButtonMode = when (this) {
    BottomRightButtonMode.Profile -> BottomRightButtonMode.History
    BottomRightButtonMode.History -> BottomRightButtonMode.Devices
    BottomRightButtonMode.Devices -> BottomRightButtonMode.Profile
}

fun BottomRightButtonMode.prev(): BottomRightButtonMode = when (this) {
    BottomRightButtonMode.Profile -> BottomRightButtonMode.Devices
    BottomRightButtonMode.History -> BottomRightButtonMode.Profile
    BottomRightButtonMode.Devices -> BottomRightButtonMode.History
}

@Composable
fun MainNavigation(
    windowSizeClass: WindowSizeClass,
    onDismiss: () -> Unit = {}
) {
    val messageHandler: MessageHandler = koinInject()
    val viewModel: MainScreenViewModel = koinViewModel()
    val deviceConfig: DeviceConfig = koinInject()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val discoveredDevices = (uiState as? MainScreenUiState.Success)?.data ?: emptyList()
    val uploadState by viewModel.clientEngine.uploadState.collectAsStateWithLifecycle()
    val downloadState by TcpDownloadService.downloadState.collectAsStateWithLifecycle()
    val googleProfile by deviceConfig.googleProfileFlow.collectAsStateWithLifecycle()

    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var showPairingModal by remember { mutableStateOf(false) }
    var activeExpandedMode by remember { mutableStateOf(SheetExpandedMode.Photos) }
    var bottomRightMode by remember { mutableStateOf(BottomRightButtonMode.Profile) }

    // Infinite Roller Offset (Pixels). Center is 0f.
    val rollerOffset = remember { Animatable(0f) }
    var isMagneticLocked by remember { mutableStateOf(false) }
    var hasMagneticHapticTriggered by remember { mutableStateOf(false) }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val mainListState = rememberLazyListState()
    val historyListState = rememberLazyListState()

    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val isDownloading = downloadState.isDownloading
    val isUploading = uploadState.isUploading
    val isTransferActive = isDownloading || isUploading || downloadState.isSuccess || uploadState.isSuccess

    val islandState by remember {
        derivedStateOf {
            when {
                TopAppBarState.isProfileExpanded && isTransferActive -> IslandContentState.EXPANDED_TRANSFER
                TopAppBarState.isProfileExpanded -> IslandContentState.EXPANDED_PROFILE
                isTransferActive -> IslandContentState.COLLAPSED_TRANSFER
                else -> IslandContentState.IDLE
            }
        }
    }

    // Dynamic Island bouncy expansion (Avatar/Profile/Transfer) from bottom-right
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val expandedWidth = screenWidth - 32.dp

    val islandWidth by animateDpAsState(
        targetValue = if (islandState == IslandContentState.EXPANDED_TRANSFER || islandState == IslandContentState.EXPANDED_PROFILE) expandedWidth else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "islandWidth"
    )
    val islandHeight by animateDpAsState(
        targetValue = when (islandState) {
            IslandContentState.EXPANDED_TRANSFER -> 180.dp
            IslandContentState.EXPANDED_PROFILE -> 140.dp
            else -> 56.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "islandHeight"
    )

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

                val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
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
            modifier = Modifier.zIndex(if (TopAppBarState.isProfileExpanded) 3f else 0f),
            onDismiss = onDismiss,
            sheetContent = { expansionFraction, currentTier, halfHeightDp, expandTo, collapseToHalf ->
                // Automatically cycle roller button to History when expanding, and reset to Profile when collapsed
                val targetRollerMode = remember(expansionFraction) {
                    when {
                        expansionFraction >= 0.25f -> BottomRightButtonMode.History
                        expansionFraction <= 0.05f -> BottomRightButtonMode.Profile
                        else -> bottomRightMode
                    }
                }

                LaunchedEffect(targetRollerMode) {
                    if (targetRollerMode != bottomRightMode && !isMagneticLocked) {
                        val buttonHeightPx = with(density) { 56.dp.toPx() }
                        val targetY = if (targetRollerMode == BottomRightButtonMode.History) -buttonHeightPx else buttonHeightPx
                        try {
                            rollerOffset.animateTo(targetY, spring(stiffness = Spring.StiffnessMediumLow))
                            bottomRightMode = targetRollerMode
                        } finally {
                            withContext(NonCancellable) {
                                rollerOffset.snapTo(0f)
                            }
                        }
                    }
                }

                LaunchedEffect(expansionFraction <= 0.05f) {
                    if (expansionFraction <= 0.05f) {
                        activeExpandedMode = SheetExpandedMode.Photos
                    }
                }

                val isDark = isSystemInDarkTheme()
                val sheetBgColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White
                val sheetContentBackdrop = rememberLayerBackdrop()
                val bottomBarBackdrop = rememberLayerBackdrop()

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val availableWidth = maxWidth
                    val isExpanded = islandState == IslandContentState.EXPANDED_TRANSFER || islandState == IslandContentState.EXPANDED_PROFILE

                    // Invisible dismissal layer to catch taps outside the expanded island
                    if (isExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(25f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { TopAppBarState.isProfileExpanded = false }
                                )
                        )
                    }

                    // 1. CAPTURED LAYER FOR ROLLER / PROFILE / HISTORY BUTTON & DYNAMIC ISLAND:
                    // Contains the Sheet background + Tabs screens (Photos/Audio/Files/History) + Navboard pill + Icons + Highlighter
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(bottomBarBackdrop)
                    ) {
                        // 1A. Captured Sheet Layer: Contains sheet background + content (flows under the navbar)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(sheetContentBackdrop)
                        ) {
                            // Base background surface for active liquid glass sampling across all states
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(sheetBgColor)
                            )

                            // 50% Resting Carousel
                            if (expansionFraction < 0.35f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((halfHeightDp - 88.dp).coerceAtLeast(0.dp))
                                        .graphicsLayer {
                                            alpha = (1f - (expansionFraction / 0.22f)).coerceIn(0f, 1f)
                                            translationY = -(36.dp * expansionFraction).toPx()
                                        },
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
                            }

                            // Expanded Media Picker Tray / History View
                            // Full size so items flow and scroll UNDER the floating bottom navbar!
                            if (expansionFraction >= 0.15f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(32.dp))
                                        .graphicsLayer {
                                            val contentProgress = ((expansionFraction - 0.18f) / 0.35f).coerceIn(0f, 1f)
                                            alpha = contentProgress
                                            translationY = (20.dp * (1f - contentProgress)).toPx()
                                        }
                                ) {
                                    if (activeExpandedMode == SheetExpandedMode.History) {
                                        HistoryScreen(
                                            modifier = Modifier.fillMaxSize(),
                                            listState = historyListState,
                                        )
                                    } else {
                                        MediaPickerTray(
                                            backdrop = contentBackdrop,
                                            currentTab =
                                                when (activeExpandedMode) {
                                                    SheetExpandedMode.Photos -> MediaTrayTab.PhotosAndVideos
                                                    SheetExpandedMode.Audio -> MediaTrayTab.Audio
                                                    SheetExpandedMode.Files -> MediaTrayTab.Files
                                                    else -> MediaTrayTab.PhotosAndVideos
                                                },
                                            onTabChange = { tab ->
                                                activeExpandedMode = when (tab) {
                                                    MediaTrayTab.PhotosAndVideos -> SheetExpandedMode.Photos
                                                    MediaTrayTab.Audio -> SheetExpandedMode.Audio
                                                    MediaTrayTab.Files -> SheetExpandedMode.Files
                                                }
                                            },
                                            onSend = { uris ->
                                                val target = selectedDevice ?: discoveredDevices.firstOrNull()
                                                if (target != null) {
                                                    sendFilesToTarget(target, uris)
                                                }
                                                collapseToHalf()
                                            },
                                            onClose = { collapseToHalf() },
                                        )
                                    }
                                }
                            }
                        }

                        // 1B. Floating Navboard Pill (Draws on top of sheet, samples sheetContentBackdrop)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 16.dp, bottom = 14.dp)
                                .zIndex(5f)
                        ) {
                            MorphingSheetNavPill(
                                expansionFraction = expansionFraction,
                                totalAvailableWidthDp = availableWidth,
                                halfHeightDp = halfHeightDp,
                                actionText = if (effectiveDevices.isNotEmpty()) "Send File" else "Pair Device",
                                onActionClick = {
                                    if (effectiveDevices.isNotEmpty()) {
                                        activeExpandedMode = SheetExpandedMode.Photos
                                        expandTo(SheetTier.High)
                                    } else {
                                        showPairingModal = true
                                    }
                                },
                                selectedMode = activeExpandedMode,
                                onSelectMode = { activeExpandedMode = it },
                                backdrop = sheetContentBackdrop,
                                modifier = Modifier.zIndex(5f)
                            )
                        }
                    }

                    // 2. FLOATING ROLLER / PROFILE / HISTORY BUTTON LAYER (Draws on top, samples bottomBarBackdrop!)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 16.dp, bottom = 14.dp)
                            .zIndex(10f)
                    ) {
                        // Endless Magnetic Roller Button (Devices <-> Profile <-> History)
                        val isExpanded = islandState == IslandContentState.EXPANDED_TRANSFER || islandState == IslandContentState.EXPANDED_PROFILE

                        val buttonHeightPx = with(density) { 56.dp.toPx() }
                        val snapThresholdPx = buttonHeightPx * 0.40f

                        val currentY = rollerOffset.value
                        val pullProgress = (Math.abs(currentY) / buttonHeightPx).coerceIn(0f, 1f)

                        val devicesConfig = LiquidGlassPresets.IconButton
                        val activeDevicesConfig = devicesConfig.copy(
                            surfaceTint = MaterialTheme.colorScheme.primary,
                            surfaceTintAlpha = 0.8f
                        )
                        val profileConfig = LiquidGlassPresets.ProfileIconButton
                        val profileIslandConfig = LiquidGlassPresets.ProfileIsland

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .zIndex(if (isExpanded) 100f else 10f)
                                .graphicsLayer {
                                    alpha = 1f
                                    scaleX = 1f
                                    scaleY = 1f
                                    clip = false
                                }
                                .size(islandWidth, islandHeight)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = { isMagneticLocked = false; hasMagneticHapticTriggered = false },
                                        onVerticalDrag = { _, dragAmount ->
                                            if (isMagneticLocked || isExpanded) return@detectVerticalDragGestures

                                             val nextY = rollerOffset.value + dragAmount
                                             if (Math.abs(nextY) > snapThresholdPx) {
                                                 isMagneticLocked = true
                                                 haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                                 scope.launch {
                                                     val targetY = if (nextY > 0) buttonHeightPx else -buttonHeightPx
                                                     rollerOffset.animateTo(targetY, spring(stiffness = Spring.StiffnessMediumLow))

                                                     bottomRightMode = if (nextY > 0) bottomRightMode.prev() else bottomRightMode.next()
                                                     rollerOffset.snapTo(0f)
                                                 }
                                             } else {
                                                 scope.launch { rollerOffset.snapTo(nextY) }
                                             }
                                         },
                                        onDragEnd = {
                                            if (!isMagneticLocked) {
                                                scope.launch { rollerOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                            }
                                            isMagneticLocked = false
                                        }
                                    )
                                }
                        ) {
                            val nextMode = if (currentY > 0) bottomRightMode.prev() else bottomRightMode.next()

                            // 1. Current Button / Expanded Island
                            Box(modifier = Modifier.graphicsLayer {
                                translationY = if (isExpanded) 0f else currentY
                                alpha = if (isExpanded) 1f else 1f - (pullProgress * 1.2f).coerceIn(0f, 1f)
                                scaleX = if (isExpanded) 1f else 1f - (pullProgress * 0.4f)
                                scaleY = if (isExpanded) 1f else 1f - (pullProgress * 0.4f)
                            }) {
                                if (bottomRightMode == BottomRightButtonMode.Profile || isExpanded) {
                                    // Dynamic Island Component
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isTransferActive && !isExpanded) {
                                            TransferProgressRing(
                                                progress = if (isDownloading) downloadState.progress else uploadState.aggregateProgress,
                                                modifier = Modifier.size(64.dp)
                                            )
                                        }
                                        LiquidGlassIconButton(
                                            onClick = {
                                                if (!isMagneticLocked && (bottomRightMode == BottomRightButtonMode.Profile || isExpanded)) {
                                                    TopAppBarState.isProfileExpanded = !TopAppBarState.isProfileExpanded
                                                    if (TopAppBarState.isProfileExpanded) TopAppBarState.isSearchExpanded = false
                                                }
                                            },
                                            width = islandWidth,
                                            height = islandHeight,
                                            backdrop = bottomBarBackdrop,
                                            config = if (isExpanded) profileIslandConfig else profileConfig
                                        ) {
                                            AnimatedContent(
                                                targetState = islandState,
                                                transitionSpec = {
                                                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                                                },
                                                label = "islandContent"
                                            ) { state ->
                                                when (state) {
                                                    IslandContentState.EXPANDED_TRANSFER -> {
                                                        ExpandedTransferContent(
                                                            downloadState = downloadState,
                                                            uploadState = uploadState,
                                                            onCancel = {
                                                                if (isDownloading) TcpDownloadService.cancelDownload(context)
                                                                else viewModel.clientEngine.cancelUpload(context)
                                                                TopAppBarState.isProfileExpanded = false
                                                            }
                                                        )
                                                    }
                                                    IslandContentState.EXPANDED_PROFILE -> {
                                                        ExpandedProfileContent(
                                                            profile = googleProfile,
                                                            onSignIn = {
                                                                val activity = context as? android.app.Activity
                                                                if (activity != null) {
                                                                    scope.launch {
                                                                        val credential = GoogleSignInManager.signIn(activity)
                                                                        val email = credential?.let { GoogleSignInManager.applyToDeviceConfig(it, deviceConfig) }
                                                                        if (email != null) {
                                                                            Toast.makeText(context, resources.getString(R.string.google_signed_in_as, email), Toast.LENGTH_LONG).show()
                                                                        } else {
                                                                            Toast.makeText(context, resources.getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                    IslandContentState.COLLAPSED_TRANSFER -> {
                                                        val currentPeerPicture = if (downloadState.isDownloading || downloadState.isSuccess) downloadState.peerPicture else uploadState.peerPicture
                                                        TransferIcon(
                                                            isDownloading = isDownloading,
                                                            isUploading = isUploading,
                                                            modifier = Modifier.size(32.dp),
                                                            peerPicture = currentPeerPicture
                                                        )
                                                    }
                                                    else -> {
                                                        CollapsedProfileContent(
                                                            profile = googleProfile,
                                                            modifier = Modifier.size(32.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Normal Devices / History Button
                                    RollerButtonItem(
                                        mode = bottomRightMode,
                                        isSettled = !isMagneticLocked && Math.abs(currentY) < 2f,
                                        showPreviewDevices = showPreviewDevices,
                                        activeDevicesConfig = activeDevicesConfig,
                                        devicesConfig = devicesConfig,
                                        profileConfig = profileConfig,
                                        googleProfile = googleProfile,
                                        contentBackdrop = bottomBarBackdrop,
                                        onTogglePreview = { showPreviewDevices = !showPreviewDevices },
                                        onExpandProfile = { TopAppBarState.isProfileExpanded = !TopAppBarState.isProfileExpanded },
                                        onOpenHistory = {
                                            activeExpandedMode = SheetExpandedMode.History
                                            expandTo(SheetTier.High)
                                        }
                                    )
                                }
                            }

                            // 2. Incoming Button (Magnetizes from top or bottom)
                            if (!isExpanded && Math.abs(currentY) > 1f) {
                                val incomingY = currentY - (if (currentY > 0) buttonHeightPx else -buttonHeightPx)
                                Box(modifier = Modifier.graphicsLayer {
                                    translationY = incomingY
                                    alpha = (pullProgress * 1.5f).coerceIn(0f, 1f)
                                    scaleX = 0.6f + (pullProgress * 0.4f)
                                    scaleY = 0.6f + (pullProgress * 0.4f)
                                }) {
                                    RollerButtonItem(
                                        mode = nextMode,
                                        isSettled = false,
                                        showPreviewDevices = showPreviewDevices,
                                        activeDevicesConfig = activeDevicesConfig,
                                        devicesConfig = devicesConfig,
                                        profileConfig = profileConfig,
                                        googleProfile = googleProfile,
                                        contentBackdrop = bottomBarBackdrop,
                                        onTogglePreview = {},
                                        onExpandProfile = {},
                                        onOpenHistory = {
                                            activeExpandedMode = SheetExpandedMode.History
                                            expandTo(SheetTier.High)
                                        }
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

        // Hidden while the onboarding sheet owns the screen
        if (!showOnboarding) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.zIndex(if (TopAppBarState.isSearchExpanded) 3f else 1f)
        ) {
            FloatingTopAppBar(
                backdrop = contentBackdrop
            )
        }
        }

        // Pair Request Prompt
        incomingPairRequest?.let { req ->
            PairingRequestDialog(
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
            OnboardingSheet(
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
 * Living Liquid Glass Bottom Navbar:
 * Single authentic liquid glass component that sits at the bottom of the sheet.
 * At rest: Solid opaque black action button (0% background refraction, authentic specular liquid glare).
 * As sheet expands: Dynamically reduces surface tint alpha, shows refractions of media passing underneath,
 * bounces in the 4 tabs, and activates the liquid glass highlighter lens.
 */
@Composable
private fun MorphingSheetNavPill(
    expansionFraction: Float,
    totalAvailableWidthDp: Dp,
    halfHeightDp: Dp,
    actionText: String,
    onActionClick: () -> Unit,
    selectedMode: SheetExpandedMode,
    onSelectMode: (SheetExpandedMode) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    // Sizing: Fixed width alongside the 56dp roller button (with 12dp start padding, 16dp end padding, 14dp gap)
    val fixedWidth = (totalAvailableWidthDp - 16.dp - 12.dp - 56.dp - 14.dp).coerceAtLeast(160.dp)
    val fixedHeight = 56.dp
    val samplingHeight = (halfHeightDp * 0.55f).coerceAtLeast(220.dp)
    val lensHeight = samplingHeight * 1.12f

    val items = remember(selectedMode, onSelectMode) {
        listOf(
            SegmentedControlItem(
                title = "Photos",
                icon = MaterialSymbols.Photo,
                isSelected = selectedMode == SheetExpandedMode.Photos,
                onClick = { onSelectMode(SheetExpandedMode.Photos) },
            ),
            SegmentedControlItem(
                title = "Audio",
                icon = MaterialSymbols.MusicNote,
                isSelected = selectedMode == SheetExpandedMode.Audio,
                onClick = { onSelectMode(SheetExpandedMode.Audio) },
            ),
            SegmentedControlItem(
                title = "Files",
                icon = MaterialSymbols.Folder,
                isSelected = selectedMode == SheetExpandedMode.Files,
                onClick = { onSelectMode(SheetExpandedMode.Files) },
            ),
        )
    }

    // Single unified LiquidGlassSegmentedControl component!
    LiquidGlassSegmentedControl(
        items = items,
        backdrop = backdrop,
        totalWidth = fixedWidth,
        visibleHeight = fixedHeight,
        samplingHeight = samplingHeight,
        lensHeight = lensHeight,
        expansionFraction = expansionFraction,
        actionText = actionText,
        onActionClick = onActionClick,
        modifier = modifier.size(fixedWidth, fixedHeight),
    )
}

/**
 * Shared renderer for the individual items inside the endless roller (Devices, Profile, History).
 */
@Composable
private fun RollerButtonItem(
    mode: BottomRightButtonMode,
    isSettled: Boolean,
    showPreviewDevices: Boolean,
    activeDevicesConfig: LiquidGlassConfig,
    devicesConfig: LiquidGlassConfig,
    profileConfig: LiquidGlassConfig,
    googleProfile: GoogleProfile,
    contentBackdrop: Backdrop?,
    onTogglePreview: () -> Unit,
    onExpandProfile: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    LiquidGlassIconButton(
        onClick = {
            if (isSettled) {
                when (mode) {
                    BottomRightButtonMode.Devices -> onTogglePreview()
                    BottomRightButtonMode.Profile -> onExpandProfile()
                    BottomRightButtonMode.History -> onOpenHistory()
                }
            }
        },
        backdrop = contentBackdrop,
        config = when (mode) {
            BottomRightButtonMode.Profile -> profileConfig
            BottomRightButtonMode.Devices -> if (showPreviewDevices) activeDevicesConfig else devicesConfig
            BottomRightButtonMode.History -> devicesConfig
        }
    ) {
        when (mode) {
            BottomRightButtonMode.Devices -> {
                Icon(
                    imageVector = MaterialSymbols.Devices,
                    contentDescription = "Toggle Preview Connected Devices",
                    tint = if (showPreviewDevices) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            BottomRightButtonMode.Profile -> {
                CollapsedProfileContent(
                    profile = googleProfile,
                    modifier = Modifier.size(32.dp)
                )
            }
            BottomRightButtonMode.History -> {
                Icon(
                    imageVector = MaterialSymbols.History,
                    contentDescription = "Transfer History",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
