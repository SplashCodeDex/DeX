package com.dexstudios.dex

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import com.dexstudios.dex.ui.components.SheetSearchIsland
import com.dexstudios.dex.ui.components.island.*
import com.dexstudios.dex.ui.history.HistoryState
import com.dexstudios.dex.ui.state.TopIslandState
import com.dexstudios.dex.ui.state.ProfileExpansionStage
import com.dexstudios.dex.ui.state.NavPillExpansionStage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber

enum class BottomRightButtonMode {
    // [EXPERIMENTED BUTTON - DO NOT TOUCH] Toggle preview mock devices.
    // This is strictly an experimented button that stays where it was in the bottom-right roller button, never in the navbar collapsible.
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
    var lastMediaMode by remember { mutableStateOf(SheetExpandedMode.Photos) }
    var bottomRightMode by remember { mutableStateOf(BottomRightButtonMode.Profile) }
    var showPlayground by remember { mutableStateOf(false) }

    LaunchedEffect(activeExpandedMode) {
        if (activeExpandedMode != SheetExpandedMode.History) {
            lastMediaMode = activeExpandedMode
        }
    }

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

    val selectedMediaUris = remember { mutableStateListOf<Uri>() }
    val uriSizeCache = remember { mutableMapOf<Uri, Long>() }
    var totalSelectedBytes by remember { mutableLongStateOf(0L) }
    var isCounterPillExpanded by remember { mutableStateOf(true) }
    var isCounterBigIslandExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedMediaUris.toList()) {
        if (selectedMediaUris.isEmpty()) {
            totalSelectedBytes = 0L
            isCounterBigIslandExpanded = false
        } else {
            isCounterPillExpanded = true
            withContext(Dispatchers.IO) {
                var sum = 0L
                for (uri in selectedMediaUris) {
                    val cached = uriSizeCache[uri]
                    if (cached != null) {
                        sum += cached
                    } else {
                        val size = Formatters.resolveFileSize(context, uri)
                        uriSizeCache[uri] = size
                        sum += size
                    }
                }
                totalSelectedBytes = sum
            }
        }
    }

    val isDownloading = downloadState.isDownloading
    val isUploading = uploadState.isUploading
    val isTransferActive = isDownloading || isUploading || downloadState.isSuccess || uploadState.isSuccess

    val islandState by remember {
        derivedStateOf {
            when {
                TopIslandState.profileStage == ProfileExpansionStage.FullIsland && isTransferActive -> IslandContentState.EXPANDED_TRANSFER
                TopIslandState.profileStage == ProfileExpansionStage.FullIsland -> IslandContentState.EXPANDED_PROFILE
                TopIslandState.profileStage == ProfileExpansionStage.NamePill -> IslandContentState.NAME_PILL_PROFILE
                isTransferActive -> IslandContentState.COLLAPSED_TRANSFER
                else -> IslandContentState.IDLE
            }
        }
    }

    // Dynamic Island bouncy expansion (Avatar/Profile/Transfer) from bottom-right
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val expandedWidth = screenWidth - 32.dp
    // Dynamic Island bouncy expansion variables moved to inner scope

    // Transient optical content blur during profile morphing (8dp peak, 75ms rise, spring dissipate)
    val profileContentBlur = remember { Animatable(0f) }
    var hasProfileBlurSettledInitial by remember { mutableStateOf(false) }

    LaunchedEffect(TopIslandState.profileStage) {
        if (!hasProfileBlurSettledInitial) {
            hasProfileBlurSettledInitial = true
            return@LaunchedEffect
        }
        profileContentBlur.animateTo(
            targetValue = DynamicContentBlurConfig.Default.maxBlur.value,
            animationSpec = DynamicContentBlurConfig.Default.riseAnimationSpec
        )
        profileContentBlur.animateTo(
            targetValue = 0f,
            animationSpec = DynamicMotionConfig.Default.springSpec(
                isExpanded = TopIslandState.profileStage != ProfileExpansionStage.Collapsed
            )
        )
    }

    // Onboarding state hoisted so both the sheet content and the overlay below can react to it
    val onboardingPrefs = remember { context.getSharedPreferences("dex_onboarding", android.content.Context.MODE_PRIVATE) }
    var showOnboarding by remember { mutableStateOf(!onboardingPrefs.getBoolean("onboarding_done", false)) }

    LaunchedEffect(showOnboarding) {
        TopIslandState.isOnboardingVisible = showOnboarding
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
                val workRequest = com.dexstudios.dex.network.UploadWorkRequestFactory.create(
                    device = target,
                    urisJson = urisJson
                )

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
        var navPillStage by remember { mutableStateOf(NavPillExpansionStage.FullTabs) }

        // Predictive back gesture handling for expanded overlays (profile/search/devices/tabs/selection)
        PredictiveBackHandler(
            enabled = isCounterBigIslandExpanded ||
                selectedMediaUris.isNotEmpty() ||
                TopIslandState.isAnyProfileExpanded ||
                HistoryState.isSearchExpanded ||
                showPreviewDevices ||
                navPillStage != NavPillExpansionStage.IconOnly
        ) { progressFlow ->
            try {
                progressFlow.collect { /* progress */ }
                if (isCounterBigIslandExpanded) {
                    isCounterBigIslandExpanded = false
                } else if (selectedMediaUris.isNotEmpty()) {
                    if (isCounterPillExpanded) {
                        isCounterPillExpanded = false
                    } else {
                        selectedMediaUris.clear()
                    }
                } else if (TopIslandState.profileStage != ProfileExpansionStage.Collapsed) {
                    TopIslandState.profileStage = ProfileExpansionStage.Collapsed
                } else if (showPreviewDevices) {
                    showPreviewDevices = false
                } else if (navPillStage == NavPillExpansionStage.FullTabs) {
                    navPillStage = NavPillExpansionStage.IconAndLabel
                } else if (navPillStage == NavPillExpansionStage.IconAndLabel) {
                    navPillStage = NavPillExpansionStage.IconOnly
                }
                HistoryState.isSearchExpanded = false
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
            modifier = Modifier.zIndex(if (TopIslandState.isProfileExpanded) 3f else 0f),
            onDismiss = onDismiss,
            sheetContent = { expansionFraction, currentTier, halfHeightDp, expandTo, collapseToHalf ->
                // Automatically cycle roller button to History when expanding History, and reset to Profile when fully collapsed
                val targetRollerMode = remember(expansionFraction, activeExpandedMode) {
                    when {
                        activeExpandedMode == SheetExpandedMode.History && expansionFraction >= 0.25f -> BottomRightButtonMode.History
                        expansionFraction <= 0.05f -> BottomRightButtonMode.Profile
                        else -> bottomRightMode
                    }
                }

                LaunchedEffect(targetRollerMode) {
                    if (targetRollerMode != bottomRightMode && !isMagneticLocked) {
                        val buttonHeightPx = with(density) { DynamicDimensions.PillDefault.collapsedHeight.toPx() }
                        val targetY = if (targetRollerMode == BottomRightButtonMode.History) -buttonHeightPx else buttonHeightPx
                        try {
                            rollerOffset.animateTo(
                                targetY,
                                DynamicMotionConfig.Default.springSpec(isExpanded = true)
                            )
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
                        activeExpandedMode = lastMediaMode
                        navPillStage = NavPillExpansionStage.FullTabs
                        TopIslandState.collapseProfile()
                        selectedMediaUris.clear()
                    }
                }

                val isDark = isSystemInDarkTheme()
                val sheetBgColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White
                val sheetContentBackdrop = rememberLayerBackdrop()
                val bottomBarBackdrop = rememberLayerBackdrop()

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val availableWidth = maxWidth
                    val rowSpace = (availableWidth - 12.dp - 16.dp).coerceAtLeast(0.dp)
                    val namePillTargetWidth = (rowSpace - 120.dp - 10.dp).coerceIn(180.dp, 210.dp)
                    val mediaSelectedItemCount = selectedMediaUris.size

                    val isHistoryActive = activeExpandedMode == SheetExpandedMode.History && expansionFraction >= 0.15f
                    val isSiblingExpanded = TopIslandState.profileStage != ProfileExpansionStage.Collapsed || isHistoryActive || showPreviewDevices
                    val isAnyExpanded = isSiblingExpanded || (selectedMediaUris.isNotEmpty() && (isCounterPillExpanded || isCounterBigIslandExpanded))

                    val isFullIslandExpanded = islandState == IslandContentState.EXPANDED_TRANSFER ||
                        islandState == IslandContentState.EXPANDED_PROFILE
                    val isAnyIslandExpanded = isFullIslandExpanded || islandState == IslandContentState.NAME_PILL_PROFILE || isCounterBigIslandExpanded

                    val isCurrentRollerItemExpanded = when {
                        isFullIslandExpanded -> true
                        bottomRightMode == BottomRightButtonMode.Profile -> TopIslandState.profileStage != ProfileExpansionStage.Collapsed
                        bottomRightMode == BottomRightButtonMode.History -> isHistoryActive
                        bottomRightMode == BottomRightButtonMode.Devices -> showPreviewDevices
                        else -> false
                    }

                    val rollerExpansionTriggerKey = when {
                        bottomRightMode == BottomRightButtonMode.Profile -> TopIslandState.profileStage
                        bottomRightMode == BottomRightButtonMode.History -> isHistoryActive
                        bottomRightMode == BottomRightButtonMode.Devices -> showPreviewDevices
                        else -> false
                    }
                    val rollerAnticipationState = rememberExpandingAnticipationPhysics(
                        isExpanded = isCurrentRollerItemExpanded,
                        triggerKey = rollerExpansionTriggerKey,
                        anchor = ExpansionAnchor.End,
                        motion = DynamicMotionConfig.Default,
                        fluidity = DynamicFluidityConfig.Default,
                    )
                    val canExpandRollerBounds = if (isCurrentRollerItemExpanded) rollerAnticipationState.canExpandBounds else false

                    val islandMotionSpec = remember(isCurrentRollerItemExpanded) {
                        DynamicMotionConfig.Default.resolveSpringSpec(isCurrentRollerItemExpanded)
                    }

                    val islandWidth by animateDpAsState(
                        targetValue = when {
                            !isCurrentRollerItemExpanded -> DynamicDimensions.PillDefault.collapsedWidth
                            !canExpandRollerBounds -> when {
                                bottomRightMode == BottomRightButtonMode.Profile && TopIslandState.profileStage == ProfileExpansionStage.FullIsland -> namePillTargetWidth
                                else -> DynamicDimensions.PillDefault.collapsedWidth
                            }
                            isFullIslandExpanded -> expandedWidth
                            bottomRightMode == BottomRightButtonMode.Profile -> when {
                                TopIslandState.profileStage == ProfileExpansionStage.NamePill -> namePillTargetWidth
                                else -> DynamicDimensions.PillDefault.collapsedWidth
                            }
                            bottomRightMode == BottomRightButtonMode.History -> if (isHistoryActive) DynamicDimensions.CompactPill.expandedWidth else DynamicDimensions.PillDefault.collapsedWidth
                            bottomRightMode == BottomRightButtonMode.Devices -> if (showPreviewDevices) DynamicDimensions.CompactPill.expandedWidth else DynamicDimensions.PillDefault.collapsedWidth
                            else -> DynamicDimensions.PillDefault.collapsedWidth
                        },
                        animationSpec = islandMotionSpec,
                        label = "islandWidth"
                    )
                    val islandHeight by animateDpAsState(
                        targetValue = when {
                            !isCurrentRollerItemExpanded || !canExpandRollerBounds -> DynamicDimensions.PillDefault.collapsedHeight
                            isFullIslandExpanded -> when (islandState) {
                                IslandContentState.EXPANDED_TRANSFER -> DynamicDimensions.TransferIsland.expandedHeight
                                else -> DynamicDimensions.FullIsland.expandedHeight
                            }
                            else -> DynamicDimensions.PillDefault.collapsedHeight
                        },
                        animationSpec = islandMotionSpec,
                        label = "islandHeight"
                    )

                    LaunchedEffect(expansionFraction < 0.15f) {
                        if (expansionFraction < 0.15f) {
                            navPillStage = NavPillExpansionStage.FullTabs
                        }
                    }

                    val navPillAlpha by animateFloatAsState(
                        targetValue = if (isFullIslandExpanded || isCounterBigIslandExpanded) 0f else 1f,
                        animationSpec = spring(
                            dampingRatio = if (isFullIslandExpanded || isCounterBigIslandExpanded) 0.50f else 0.65f,
                            stiffness = 380f
                        ),
                        label = "navPillAlpha"
                    )

                    // Invisible dismissal layer to catch taps outside the expanded island
                    if (isAnyIslandExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(25f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        TopIslandState.profileStage = ProfileExpansionStage.Collapsed
                                        isCounterBigIslandExpanded = false
                                    }
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
                                            selectedUris = selectedMediaUris,
                                            onClose = { collapseToHalf() },
                                        )
                                    }
                                }
                            }
                        }

                        // Auto-regulate navbar stage based on siblings and media items:
                        // If items selected and counter is expanded: drop to IconOnly to leave room for selection counter
                        // If sibling is expanded (Profile NamePill, History, Devices) or counter is collapsed: drop from FullTabs to IconAndLabel
                        val effectiveNavStage: NavPillExpansionStage = when {
                            selectedMediaUris.isNotEmpty() -> NavPillExpansionStage.IconOnly
                            isAnyExpanded -> when (navPillStage) {
                                NavPillExpansionStage.FullTabs -> NavPillExpansionStage.IconAndLabel
                                else -> navPillStage
                            }
                            else -> navPillStage
                        }
                        val isTabsExpanded = effectiveNavStage == NavPillExpansionStage.FullTabs

                        // Dismissal layer for expanded nav tabs: tapping anywhere on sheet collapses tabs back to label pill
                        if (isTabsExpanded && expansionFraction >= 0.15f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(15f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { navPillStage = NavPillExpansionStage.IconAndLabel }
                                    )
                            )
                        }

                        // 1B. Floating Navboard Pill (Draws on top of sheet, samples sheetContentBackdrop)
                        if (navPillAlpha > 0.01f) {
                            val isDevicePaired = AuthState.pairedFingerprints.isNotEmpty() || (showPreviewDevices && effectiveDevices.isNotEmpty())
                            val actionText = if (isDevicePaired) "Send Files" else "Pair Device"
                            val isMediaOrHistoryActive = expansionFraction >= 0.15f

                            // Auto-collapse nav tabs when sibling expands
                            LaunchedEffect(isAnyExpanded) {
                                if (isAnyExpanded && navPillStage == NavPillExpansionStage.FullTabs) {
                                    navPillStage = NavPillExpansionStage.IconAndLabel
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 16.dp, bottom = 14.dp)
                                    .zIndex(if (isTabsExpanded) 22f else 5f)
                                    .graphicsLayer {
                                        alpha = navPillAlpha
                                    }
                            ) {
                                MorphingSheetNavPill(
                                    isMediaActive = isMediaOrHistoryActive,
                                    stage = effectiveNavStage,
                                    onAdvanceStage = { 
                                        val nextStage = when (effectiveNavStage) {
                                            NavPillExpansionStage.IconOnly -> {
                                                if (isAnyExpanded) NavPillExpansionStage.IconAndLabel else NavPillExpansionStage.FullTabs
                                            }
                                            NavPillExpansionStage.IconAndLabel -> NavPillExpansionStage.FullTabs
                                            NavPillExpansionStage.FullTabs -> NavPillExpansionStage.IconOnly
                                        }
                                        navPillStage = nextStage
                                        if (nextStage == NavPillExpansionStage.FullTabs) {
                                            TopIslandState.collapseProfile()
                                            showPreviewDevices = false
                                            if (activeExpandedMode == SheetExpandedMode.History) {
                                                activeExpandedMode = lastMediaMode
                                            }
                                        }
                                    },
                                    onSetStage = { stage ->
                                        navPillStage = stage
                                        if (stage == NavPillExpansionStage.FullTabs) {
                                            TopIslandState.collapseProfile()
                                            showPreviewDevices = false
                                        }
                                    },
                                    selectedMode = if (activeExpandedMode == SheetExpandedMode.History) lastMediaMode else activeExpandedMode,
                                    onSelectMode = { 
                                        activeExpandedMode = it 
                                        lastMediaMode = it
                                    },
                                    actionText = actionText,
                                    onActionClick = {
                                        if (isDevicePaired) {
                                            activeExpandedMode = lastMediaMode
                                            expandTo(SheetTier.High)
                                        } else {
                                            showPairingModal = true
                                        }
                                    },
                                    totalAvailableWidthDp = availableWidth,
                                    halfHeightDp = halfHeightDp,
                                    backdrop = sheetContentBackdrop,
                                    modifier = Modifier.zIndex(if (isTabsExpanded) 22f else 5f)
                                )
                            }
                        }

                        // 1C. Floating Search Island (Draws on top of sheet, samples sheetContentBackdrop)
                        if (activeExpandedMode == SheetExpandedMode.History && expansionFraction >= 0.15f) {
                            val contentProgress = ((expansionFraction - 0.18f) / 0.35f).coerceIn(0f, 1f)
                            SheetSearchIsland(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .statusBarsPadding()
                                    .padding(top = 16.dp, end = 16.dp)
                                    .graphicsLayer {
                                        alpha = contentProgress
                                        translationY = (20.dp * (1f - contentProgress)).toPx()
                                    },
                                backdrop = sheetContentBackdrop
                            )
                        }
                    }

                    // 2. FLOATING ROLLER / SELECTION / PROFILE DOCK LAYER (Draws on top, samples bottomBarBackdrop!)
                    val isMediaOrHistoryActive = expansionFraction >= 0.15f
                    val counterPillAlpha by animateFloatAsState(
                        targetValue = if (isFullIslandExpanded) 0f else 1f,
                        animationSpec = spring(
                            dampingRatio = if (isFullIslandExpanded) 0.50f else 0.65f,
                            stiffness = 380f
                        ),
                        label = "counterPillAlpha"
                    )
                    val rollerAlpha by animateFloatAsState(
                        targetValue = if (isCounterBigIslandExpanded) 0f else 1f,
                        animationSpec = spring(
                            dampingRatio = if (isCounterBigIslandExpanded) 0.50f else 0.65f,
                            stiffness = 380f
                        ),
                        label = "rollerAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 16.dp, bottom = 14.dp)
                            .zIndex(if (isAnyIslandExpanded) 30f else 10f)
                    ) {
                        // 2A. Selection Counter Action Pill / Big Island (Aligned at BottomStart)
                        if (isMediaOrHistoryActive && counterPillAlpha > 0.01f) {
                            val counterStartOffset by animateDpAsState(
                                targetValue = if (isCounterBigIslandExpanded) 0.dp else DynamicDimensions.PillDefault.collapsedWidth + 10.dp,
                                animationSpec = spring(
                                    dampingRatio = if (isCounterBigIslandExpanded) 0.50f else 0.56f,
                                    stiffness = 301f
                                ),
                                label = "counterStartOffset"
                            )

                            SelectedItemsCounterPill(
                                selectedCount = mediaSelectedItemCount,
                                totalSizeBytes = totalSelectedBytes,
                                selectedUris = selectedMediaUris.toList(),
                                devices = effectiveDevices,
                                isExpanded = isCounterPillExpanded,
                                onExpandedChange = { isCounterPillExpanded = it },
                                isBigIslandExpanded = isCounterBigIslandExpanded,
                                onBigIslandExpandedChange = { isCounterBigIslandExpanded = it },
                                onSend = {
                                    val target = selectedDevice ?: effectiveDevices.firstOrNull()
                                    if (target != null) {
                                        sendFilesToTarget(target, selectedMediaUris.toList())
                                        selectedMediaUris.clear()
                                        totalSelectedBytes = 0L
                                        isCounterBigIslandExpanded = false
                                        collapseToHalf()
                                    } else {
                                        showPairingModal = true
                                    }
                                },
                                onSendToDevice = { device ->
                                    sendFilesToTarget(device, selectedMediaUris.toList())
                                    selectedMediaUris.clear()
                                    totalSelectedBytes = 0L
                                    isCounterBigIslandExpanded = false
                                    collapseToHalf()
                                },
                                onClear = {
                                    selectedMediaUris.clear()
                                    totalSelectedBytes = 0L
                                    isCounterBigIslandExpanded = false
                                },
                                onPairDevice = {
                                    isCounterBigIslandExpanded = false
                                    showPairingModal = true
                                },
                                totalAvailableWidthDp = availableWidth,
                                backdrop = sheetContentBackdrop,
                                isSiblingExpanded = isSiblingExpanded,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset { IntOffset(x = counterStartOffset.roundToPx(), y = 0) }
                                    .zIndex(if (isCounterBigIslandExpanded) 35f else 6f)
                                    .graphicsLayer {
                                        alpha = counterPillAlpha
                                    }
                            )
                        }

                        // 2B. Endless Magnetic Roller Button (Devices <-> Profile <-> History) (Aligned at BottomEnd)
                        if (rollerAlpha > 0.01f) {
                            val isExpanded = isFullIslandExpanded

                            val buttonHeightPx = with(density) { 56.dp.toPx() }
                            val snapThresholdPx = buttonHeightPx * 0.40f

                            val currentY = rollerOffset.value
                            val pullProgress = (Math.abs(currentY) / buttonHeightPx).coerceIn(0f, 1f)

                            val devicesConfig = LiquidGlassPresets.IconButton
                            val activeDevicesConfig = devicesConfig.copy(
                                surfaceTint = MaterialTheme.colorScheme.primary,
                                surfaceTintAlpha = 0.8f
                            )
                            val historyConfig = LiquidGlassPresets.HistoryIconButton
                            val activeHistoryConfig = historyConfig.copy(
                                surfaceTint = MaterialTheme.colorScheme.primary,
                                surfaceTintAlpha = 0.8f
                            ).withShadowProperties(LiquidGlassShadowProperties.Expanded)
                            val profileConfig = LiquidGlassPresets.ProfileIconButton
                            val profileIslandConfig = LiquidGlassPresets.ProfileIsland

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .zIndex(if (isExpanded) 100f else 10f)
                                    .graphicsLayer {
                                        translationX = rollerAnticipationState.translationX
                                        scaleX = rollerAnticipationState.scaleX
                                        scaleY = rollerAnticipationState.scaleY
                                        alpha = rollerAlpha
                                        clip = false
                                    }
                                    .size(islandWidth, islandHeight)
                                .then(
                                    if (!isCurrentRollerItemExpanded) {
                                        Modifier.pointerInput(Unit) {
                                            detectVerticalDragGestures(
                                                onDragStart = { isMagneticLocked = false; hasMagneticHapticTriggered = false },
                                                onVerticalDrag = { _, dragAmount ->
                                                    if (isMagneticLocked) return@detectVerticalDragGestures

                                                     val nextY = rollerOffset.value + dragAmount
                                                     if (Math.abs(nextY) > snapThresholdPx) {
                                                         isMagneticLocked = true
                                                         haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                                         scope.launch {
                                                             val targetY = if (nextY > 0) buttonHeightPx else -buttonHeightPx
                                                             rollerOffset.animateTo(
                                                                 targetY,
                                                                 DynamicMotionConfig.Default.springSpec(isExpanded = true)
                                                             )

                                                             bottomRightMode = if (nextY > 0) bottomRightMode.prev() else bottomRightMode.next()
                                                             rollerOffset.snapTo(0f)
                                                         }
                                                     } else {
                                                         scope.launch { rollerOffset.snapTo(nextY) }
                                                     }
                                                 },
                                                onDragEnd = {
                                                    if (!isMagneticLocked) {
                                                        scope.launch {
                                                            rollerOffset.animateTo(
                                                                0f,
                                                                DynamicMotionConfig.Default.springSpec(isExpanded = false)
                                                            )
                                                        }
                                                    }
                                                    isMagneticLocked = false
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
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
                                                if (!isMagneticLocked) {
                                                    if (isTransferActive) {
                                                        TopIslandState.profileStage = if (TopIslandState.profileStage == ProfileExpansionStage.FullIsland) {
                                                            ProfileExpansionStage.Collapsed
                                                        } else {
                                                            ProfileExpansionStage.FullIsland
                                                        }
                                                    } else if (bottomRightMode == BottomRightButtonMode.Profile) {
                                                        TopIslandState.advanceProfileStage()
                                                        if (TopIslandState.profileStage != ProfileExpansionStage.Collapsed) {
                                                            HistoryState.isSearchExpanded = false
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = true,
                                            width = islandWidth,
                                            height = islandHeight,
                                            backdrop = bottomBarBackdrop,
                                            config = if (isExpanded) profileIslandConfig else profileConfig,
                                            enableBubbleFluidity = true,
                                        ) {
                                            val currentProfileBlur = profileContentBlur.value
                                            val profileBlurModifier = if (currentProfileBlur > 0.5f) Modifier.blur(currentProfileBlur.dp) else Modifier

                                            AnimatedContent(
                                                targetState = islandState,
                                                transitionSpec = {
                                                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                                                },
                                                modifier = profileBlurModifier,
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
                                                                TopIslandState.collapseProfile()
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
                                                    IslandContentState.NAME_PILL_PROFILE -> {
                                                        ProfileNamePillContent(
                                                            profile = googleProfile,
                                                            isPro = false
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
                                        width = islandWidth,
                                        height = islandHeight,
                                        isSettled = !isMagneticLocked && Math.abs(currentY) < 2f,
                                        showPreviewDevices = showPreviewDevices,
                                        activeDevicesConfig = activeDevicesConfig,
                                        devicesConfig = devicesConfig,
                                        historyConfig = historyConfig,
                                        activeHistoryConfig = activeHistoryConfig,
                                        isHistoryActive = isHistoryActive,
                                        profileConfig = profileConfig,
                                        googleProfile = googleProfile,
                                        contentBackdrop = bottomBarBackdrop,
                                        onTogglePreview = { showPreviewDevices = !showPreviewDevices },
                                        onExpandProfile = { TopIslandState.advanceProfileStage() },
                                        onOpenHistory = {
                                            if (activeExpandedMode == SheetExpandedMode.History && currentTier == SheetTier.High) {
                                                collapseToHalf()
                                            } else {
                                                activeExpandedMode = SheetExpandedMode.History
                                                expandTo(SheetTier.High)
                                            }
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
                                        historyConfig = historyConfig,
                                        activeHistoryConfig = activeHistoryConfig,
                                        isHistoryActive = isHistoryActive,
                                        profileConfig = profileConfig,
                                        googleProfile = googleProfile,
                                        contentBackdrop = bottomBarBackdrop,
                                        onTogglePreview = {},
                                        onExpandProfile = {},
                                        onOpenHistory = {
                                            if (activeExpandedMode == SheetExpandedMode.History && currentTier == SheetTier.High) {
                                                collapseToHalf()
                                            } else {
                                                activeExpandedMode = SheetExpandedMode.History
                                                expandTo(SheetTier.High)
                                            }
                                        }
                                    )
                                }
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

        // Dynamic Experimenting Lab Launcher Button
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp)
                .zIndex(150f)
        ) {
            Button(
                onClick = { showPlayground = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSystemInDarkTheme()) Color(0xFF1E293B) else Color.White,
                    contentColor = if (isSystemInDarkTheme()) Color.White else Color.Black
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Tune,
                        contentDescription = "Dynamic Experimenting Lab",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Lab",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Dynamic Experimenting Lab Overlay
        if (showPlayground) {
            BackHandler(enabled = true) {
                showPlayground = false
            }
            DynamicIslandPlayground(
                onDismiss = { showPlayground = false },
                backdrop = contentBackdrop,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(500f)
            )
        }
    }
}

/**
 * Living Liquid Glass Bottom Navbar:
 * Full stadium expandable pill that sits at the bottom of the sheet.
 * - When resting (no media/history active): It is the full expanded stadium pill.
 *   - "Pair Device" when no device is paired at all.
 *   - "Send Files" when a device is already paired.
 * - When media/history is active: Contracts into a 56dp stadium action button showing
 *   only the active mode icon (Photos or Audio), with CodeDeX's tuned overshoot springs
 *   (0.5f/0.56f @ 170f) and tactile bubble fluidity.
 * - When tapped during active media: Expands smoothly to reveal the liquid glass nav tabs
 *   (Photos, Audio, Files), collapsing back to the active icon on selection or outside tap.
 */
@Composable
private fun MorphingSheetNavPill(
    isMediaActive: Boolean,
    stage: NavPillExpansionStage,
    onAdvanceStage: () -> Unit,
    onSetStage: (NavPillExpansionStage) -> Unit,
    selectedMode: SheetExpandedMode,
    onSelectMode: (SheetExpandedMode) -> Unit,
    actionText: String,
    onActionClick: () -> Unit,
    totalAvailableWidthDp: Dp,
    halfHeightDp: Dp,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val rowSpace = (totalAvailableWidthDp - 16.dp - 12.dp).coerceAtLeast(0.dp)
    val fullWidth = (rowSpace - DynamicDimensions.PillDefault.collapsedWidth - 14.dp).coerceAtLeast(160.dp)
    val contractedWidth = (rowSpace - DynamicDimensions.ProfilePill.expandedWidth - 10.dp).coerceIn(115.dp, DynamicDimensions.CompactPill.expandedWidth)
    val iconOnlyWidth = DynamicDimensions.PillDefault.collapsedWidth
    val height = DynamicDimensions.PillDefault.collapsedHeight
    val pillShape = CircleShape

    val isExpandedBeyondIcon = stage != NavPillExpansionStage.IconOnly

    val navPillAnticipationState = rememberExpandingAnticipationPhysics(
        isExpanded = isExpandedBeyondIcon,
        triggerKey = stage,
        anchor = ExpansionAnchor.Start,
        motion = DynamicMotionConfig.Default,
        fluidity = DynamicFluidityConfig.Default,
    )
    val shouldExpandNavBounds = if (isExpandedBeyondIcon) navPillAnticipationState.canExpandBounds else false

    val targetWidth = when {
        !isMediaActive -> fullWidth
        stage == NavPillExpansionStage.IconOnly -> iconOnlyWidth
        stage == NavPillExpansionStage.IconAndLabel -> if (shouldExpandNavBounds) contractedWidth else iconOnlyWidth
        stage == NavPillExpansionStage.FullTabs -> if (shouldExpandNavBounds) fullWidth else contractedWidth
        else -> iconOnlyWidth
    }

    val morphSpring = remember(stage) {
        DynamicMotionConfig.Default.resolveSpringSpec(stage != NavPillExpansionStage.IconOnly)
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = morphSpring,
        label = "navPillWidth"
    )



    val buttonTint = if (isDark) Color.White else Color.Black
    val contentTint = if (isDark) Color.Black else Color.White

    val glassConfig = LiquidGlassPresets.NavBar.copy(
        shape = pillShape,
        surfaceTint = buttonTint,
        surfaceTintAlpha = 0.95f
    )

    val samplingHeight = (halfHeightDp * 0.55f).coerceAtLeast(220.dp)
    val lensHeight = samplingHeight * 1.12f

    val tabItems = remember(selectedMode, onSelectMode, onSetStage) {
        listOf(
            SegmentedControlItem(
                title = "Photos",
                icon = MaterialSymbols.Photo,
                isSelected = selectedMode == SheetExpandedMode.Photos,
                onClick = {
                    if (selectedMode == SheetExpandedMode.Photos) {
                        onSetStage(NavPillExpansionStage.IconAndLabel)
                    } else {
                        onSelectMode(SheetExpandedMode.Photos)
                        onSetStage(NavPillExpansionStage.IconAndLabel)
                    }
                },
            ),
            SegmentedControlItem(
                title = "Audio",
                icon = MaterialSymbols.MusicNote,
                isSelected = selectedMode == SheetExpandedMode.Audio,
                onClick = {
                    if (selectedMode == SheetExpandedMode.Audio) {
                        onSetStage(NavPillExpansionStage.IconAndLabel)
                    } else {
                        onSelectMode(SheetExpandedMode.Audio)
                        onSetStage(NavPillExpansionStage.IconAndLabel)
                    }
                },
            ),
            SegmentedControlItem(
                title = "Files",
                icon = MaterialSymbols.Folder,
                isSelected = selectedMode == SheetExpandedMode.Files,
                onClick = {
                    if (selectedMode == SheetExpandedMode.Files) {
                        onSetStage(NavPillExpansionStage.IconAndLabel)
                    } else {
                        onSelectMode(SheetExpandedMode.Files)
                        onSetStage(NavPillExpansionStage.IconAndLabel)
                    }
                },
            ),
        )
    }

    Box(
        modifier = modifier
            .size(animatedWidth, height)
            .expandingAnticipation(navPillAnticipationState)
            .clip(pillShape),
        contentAlignment = Alignment.Center
    ) {
        if (!isMediaActive) {
            LiquidGlassIconButton(
                onClick = onActionClick,
                backdrop = backdrop,
                config = glassConfig,
                width = animatedWidth,
                height = height,
                modifier = Modifier.size(animatedWidth, height)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(pillShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = actionText,
                        color = contentTint,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            val activeIcon = when (selectedMode) {
                SheetExpandedMode.Audio -> MaterialSymbols.MusicNote
                SheetExpandedMode.Files -> MaterialSymbols.Folder
                else -> MaterialSymbols.Photo
            }
            val activeTitle = when (selectedMode) {
                SheetExpandedMode.Audio -> "Audio"
                SheetExpandedMode.Files -> "Files"
                else -> "Photos"
            }
            val blurMod = Modifier.transientContentBlur(
                trigger = stage,
                config = DynamicContentBlurConfig.Default,
                motion = DynamicMotionConfig.Default
            )

            when (stage) {
                NavPillExpansionStage.IconOnly -> {
                    // Stage 1: Just the active mode icon inside 56.dp circle
                    LiquidGlassIconButton(
                        onClick = onAdvanceStage,
                        backdrop = backdrop,
                        config = glassConfig,
                        width = animatedWidth,
                        height = height,
                        modifier = Modifier.size(animatedWidth, height)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(pillShape)
                                .then(blurMod),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = activeIcon,
                                contentDescription = activeTitle,
                                tint = contentTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                NavPillExpansionStage.IconAndLabel -> {
                    // Stage 2: 130.dp stadium pill showing Icon + Label
                    LiquidGlassIconButton(
                        onClick = onAdvanceStage,
                        backdrop = backdrop,
                        config = glassConfig,
                        width = animatedWidth,
                        height = height,
                        modifier = Modifier.size(animatedWidth, height)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(pillShape)
                                .then(blurMod),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = activeIcon,
                                contentDescription = activeTitle,
                                tint = contentTint,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeTitle,
                                color = contentTint,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                NavPillExpansionStage.FullTabs -> {
                    // Stage 3: Full liquid glass segmented control
                    LiquidGlassSegmentedControl(
                        items = tabItems,
                        backdrop = backdrop,
                        totalWidth = fullWidth,
                        visibleHeight = height,
                        samplingHeight = samplingHeight,
                        lensHeight = lensHeight,
                        expansionFraction = 1f,
                        modifier = Modifier.size(fullWidth, height)
                    )
                }
            }
        }
    }
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
    historyConfig: LiquidGlassConfig,
    activeHistoryConfig: LiquidGlassConfig,
    isHistoryActive: Boolean,
    profileConfig: LiquidGlassConfig,
    googleProfile: GoogleProfile,
    contentBackdrop: Backdrop?,
    onTogglePreview: () -> Unit,
    onExpandProfile: () -> Unit,
    onOpenHistory: () -> Unit,
    width: Dp = DynamicDimensions.PillDefault.collapsedWidth,
    height: Dp = DynamicDimensions.PillDefault.collapsedHeight,
) {
    val iconSpringScale by animateFloatAsState(
        targetValue = if (isSettled) 1.0f else 0.82f,
        animationSpec = DynamicMotionConfig.Default.springSpec(isExpanded = isSettled),
        label = "rollerIconScale"
    )

    val isItemExpanded = (mode == BottomRightButtonMode.History && isHistoryActive) ||
        (mode == BottomRightButtonMode.Devices && showPreviewDevices)
    val itemBlur = remember { Animatable(0f) }
    var hasItemBlurSettled by remember { mutableStateOf(false) }

    LaunchedEffect(isItemExpanded) {
        if (!hasItemBlurSettled) {
            hasItemBlurSettled = true
            return@LaunchedEffect
        }
        itemBlur.animateTo(
            targetValue = DynamicContentBlurConfig.Default.maxBlur.value,
            animationSpec = DynamicContentBlurConfig.Default.riseAnimationSpec
        )
        itemBlur.animateTo(
            targetValue = 0f,
            animationSpec = DynamicMotionConfig.Default.springSpec(isExpanded = isItemExpanded)
        )
    }

    val itemBlurModifier = if (itemBlur.value > 0.5f) Modifier.blur(itemBlur.value.dp) else Modifier

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
        width = width,
        height = height,
        backdrop = contentBackdrop,
        config = when (mode) {
            BottomRightButtonMode.Profile -> profileConfig
            BottomRightButtonMode.Devices -> if (showPreviewDevices) activeDevicesConfig else devicesConfig
            BottomRightButtonMode.History -> if (isHistoryActive) activeHistoryConfig else historyConfig
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = iconSpringScale
                    scaleY = iconSpringScale
                }
                .then(itemBlurModifier)
        ) {
            when (mode) {
                BottomRightButtonMode.Devices -> {
                    // [EXPERIMENTED BUTTON - DO NOT TOUCH] Toggle preview mock devices.
                    // This is strictly an experimented button that stays where it was in the bottom-right roller button, never in the navbar collapsible.
                    if (showPreviewDevices) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Devices,
                                contentDescription = "Toggle Preview Connected Devices",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Devices",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    } else {
                        Icon(
                            imageVector = MaterialSymbols.Devices,
                            contentDescription = "Toggle Preview Connected Devices",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                BottomRightButtonMode.Profile -> {
                    CollapsedProfileContent(
                        profile = googleProfile,
                        modifier = Modifier.size(32.dp)
                    )
                }
                BottomRightButtonMode.History -> {
                    if (isHistoryActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.History,
                                contentDescription = "Transfer History",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "History",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    } else {
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
    }
}
