package com.dexstudios.dex.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.joe_avatar
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.window.components.BottomDockPanel
import com.dexstudios.dex.window.components.DeviceItemUiModel
import com.dexstudios.dex.window.components.DeviceListPanel
import com.dexstudios.dex.window.components.TopActionsPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Always-visible 300dp right column of the floating dock card.
 * Hosts:
 * - TopActionsPanel (DragPillHandle, quick action buttons, status telemetry)
 * - DeviceListPanel (Discovered untrusted devices, paired trusted live/offline devices, and WAN scaffolding)
 * - BottomDockPanel (Avatar button -> Settings, 2-stage Exit Engine confirmation)
 */
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import androidx.compose.ui.unit.Dp

@Composable
fun MainMenuColumn(
    controller: DockedWindowStateController,
    onExpandFileExplorer: () -> Unit,
    onExpandSettings: () -> Unit,
    onContract: () -> Unit,
    onPairDevice: (DiscoveredDevice) -> Unit,
    onExitEngine: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    discoveryEngine: DiscoveryEngine = koinInject(),
    deviceConfig: DeviceConfig = koinInject(),
    clientEngine: ClientEngine = koinInject()
) {
    val coroutineScope = rememberCoroutineScope()
    val devicesMap by discoveryEngine.devices.collectAsState()
    val pairedFingerprints by AuthState.pairedFingerprints.collectAsState()
    val isUploading by remember(clientEngine) {
        clientEngine.uploadState
            .map { it.isUploading }
            .distinctUntilChanged()
    }.collectAsState(initial = false)
    val isClipboardSyncEnabled by deviceConfig.clipboardSyncEnabledFlow.collectAsState()

    var isDndActive by remember { mutableStateOf(false) }
    var isMirroringActive by remember { mutableStateOf(false) }

    var isContentVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(controller.isVisible) {
        if (controller.isVisible) {
            delay(80)
            isContentVisible = true
        } else {
            isContentVisible = false
        }
    }

    val isMacOS = com.dexstudios.dex.platform.DesktopEnvironment.isMacOS

    val menuTranslateY by animateDpAsState(
        targetValue = if (controller.isVisible) 0.dp else if (isMacOS) (-20).dp else 20.dp,
        animationSpec = if (controller.isVisible) DockCardAnimations.PopInMenuTranslateYSpec else tween(200, easing = FastOutSlowInEasing),
        label = "menuTranslateY"
    )

    val contentTranslateY by animateDpAsState(
        targetValue = if (isContentVisible) 0.dp else if (isMacOS) (-35).dp else 35.dp,
        animationSpec = if (isContentVisible) DockCardAnimations.PopInMenuContentTranslateYSpec else tween(200, easing = FastOutSlowInEasing),
        label = "contentTranslateY"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isContentVisible) 1f else 0f,
        animationSpec = tween(if (isContentVisible) 400 else 150),
        label = "contentAlpha"
    )

    // Partition discovered devices vs paired devices
    val (discoveredList, pairedList) = remember(devicesMap, pairedFingerprints) {
        val discovered = mutableListOf<DeviceItemUiModel>()
        val paired = mutableListOf<DeviceItemUiModel>()

        devicesMap.values.forEach { device ->
            val isPaired = pairedFingerprints.contains(device.info.fingerprint)
            val uiModel = DeviceItemUiModel(
                id = device.info.fingerprint.ifBlank { device.ip },
                alias = device.info.alias.ifBlank { device.info.deviceModel.ifBlank { "DeX Device" } },
                modelText = device.info.deviceModel,
                ip = device.ip,
                fingerprint = device.info.fingerprint,
                isPaired = isPaired,
                isOnline = true,
                batteryPercent = device.info.battery,
                isCharging = device.info.isCharging ?: false,
                wifiBand = device.info.wifiBand ?: device.info.wifiSsid,
                rawDevice = device
            )
            if (isPaired) paired.add(uiModel) else discovered.add(uiModel)
        }
        Pair(discovered, paired)
    }

    val devices = devicesMap.values.toList()

    // Mock DeXStudios fallback removed for Phase 4.2 Parity

    val serverIp = devices.firstOrNull()?.ip ?: "127.0.0.1"
    val serverPort = devices.firstOrNull()?.info?.port ?: 53317
    val serverIpPortText = "$serverIp:$serverPort"

    // Dedicated Live Screen Mirroring Window
    if (isMirroringActive) {
        val activeDeviceName = devices.firstOrNull()?.info?.alias ?: "Connected Phone"
        MirrorWindow(
            peerName = activeDeviceName,
            onClose = { isMirroringActive = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .graphicsLayer {
                translationY = menuTranslateY.toPx()
            }
    ) {
        // Top Actions Panel with DragPillHandle & Tactile QuickActionBar
        Box(modifier = Modifier.graphicsLayer { 
            translationY = contentTranslateY.toPx()
            alpha = contentAlpha
        }) {
            TopActionsPanel(
                controller = controller,
                isDndActive = isDndActive,
                onToggleDnd = { isDndActive = !isDndActive },
                isMirroringActive = isMirroringActive,
                onToggleMirror = { isMirroringActive = !isMirroringActive },
                isTransfersActive = controller.expandedPanel == ExpandedPanel.FileExplorer,
                onToggleTransfers = {
                    if (controller.expandedPanel == ExpandedPanel.FileExplorer) {
                        controller.collapsePanel()
                    } else {
                        controller.expandPanel(ExpandedPanel.FileExplorer)
                    }
                },
                isClipboardActive = isClipboardSyncEnabled,
                onToggleClipboard = {
                    deviceConfig.clipboardSyncEnabled = !deviceConfig.clipboardSyncEnabled
                },
                clipboardBadgeCount = 0,
                statusTelemetryText = if (isUploading) "Transferring" else "Ready",
                serverIpPort = serverIpPortText,
                showTelemetry = false // WPF pnlAdbStatus is hidden by default (Height=0) until connected
            )
        }

        com.dexstudios.dex.window.components.ActiveTransferDashboard(
            backdrop = com.dexstudios.dex.core.designsystem.theme.LocalBackdrop.current,
            modifier = Modifier.fillMaxWidth()
        )

        // Device List - occupies flexible viewport
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .graphicsLayer { 
                translationY = contentTranslateY.toPx()
                alpha = contentAlpha
            }
        ) {
            DeviceListPanel(
                discoveredDevices = discoveredList,
                pairedDevices = pairedList,
                onPairDevice = { item ->
                    val selectedDevice = item.rawDevice ?: devices.find { it.info.fingerprint == item.fingerprint }
                    selectedDevice?.let { onPairDevice(it) }
                },
                onSendFile = {
                    onExpandFileExplorer()
                },
                onSendClipboard = { item ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            val text = clipboard.getData(DataFlavor.stringFlavor) as? String
                            if (!text.isNullOrBlank()) {
                                println("Pushed clipboard text to device ${item.alias}: $text")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                onMirrorScreen = {
                    isMirroringActive = !isMirroringActive
                },
                onConnectAdb = { item ->
                    val targetIp = item.ip.ifBlank { "127.0.0.1" }
                    coroutineScope.launch(Dispatchers.IO) {
                        com.dexstudios.dex.desktop.AdbManager.connect(targetIp)
                    }
                },
                onDisconnectAdb = { item ->
                    val targetIp = item.ip.ifBlank { "127.0.0.1" }
                    coroutineScope.launch(Dispatchers.IO) {
                        com.dexstudios.dex.desktop.AdbManager.disconnect(targetIp)
                    }
                },
                onCopyIp = { ip ->
                    try {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(
                            StringSelection(ip),
                            null
                        )
                    } catch (_: Exception) {}
                },
                onRenameDevice = {},
                onForgetDevice = { item ->
                    AuthState.updateFingerprints(pairedFingerprints - item.fingerprint)
                }
            )
        }

        // Bottom Dock Panel (Profile avatar -> Settings, 2-stage Exit Engine)
        Box(modifier = Modifier.graphicsLayer { 
            translationY = contentTranslateY.toPx()
            alpha = contentAlpha
        }) {
            BottomDockPanel(
                onProfileClick = {
                    if (controller.expandedPanel == ExpandedPanel.Settings) {
                        controller.collapsePanel()
                    } else {
                        controller.expandPanel(ExpandedPanel.Settings)
                    }
                },
                onExitEngine = onExitEngine,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                hasActiveTransfers = isUploading,
                isMirroringActive = isMirroringActive
            )
        }
    }
}
