package com.dexstudios.dex.window

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.designsystem.components.overlay.ToastVariant
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.joe_avatar
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DeXPorts
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.overlay.OverlayManager
import com.dexstudios.dex.window.components.BottomDockPanel
import com.dexstudios.dex.window.components.DeviceItemUiModel
import com.dexstudios.dex.window.components.DeviceListPanel
import com.dexstudios.dex.window.components.LocalExternalDragState
import com.dexstudios.dex.window.components.TopActionsPanel
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
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
    clientEngine: ClientEngine = koinInject(),
    fileSender: com.dexstudios.dex.desktop.transfer.DesktopFileSendService = koinInject(),
    overlayManager: OverlayManager = koinInject(),
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
    val isDndActive by deviceConfig.dndEnabledFlow.collectAsState()

    // Pinned default send destination (surfaced in telemetry once telemetry is enabled)
    val preferredTargetFp by fileSender.preferredTargetFlow.collectAsState()

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
        targetValue = if (controller.isVisible) {
            0.dp
        } else if (isMacOS) {
            (-20).dp
        } else {
            20.dp
        },
        animationSpec = if (controller.isVisible) DockCardAnimations.PopInMenuTranslateYSpec else DockCardAnimations.HideEaseDp,
        label = "menuTranslateY",
    )

    val contentTranslateY by animateDpAsState(
        targetValue = if (isContentVisible) {
            0.dp
        } else if (isMacOS) {
            (-35).dp
        } else {
            35.dp
        },
        animationSpec = if (isContentVisible) DockCardAnimations.PopInMenuContentTranslateYSpec else DockCardAnimations.HideEaseDp,
        label = "contentTranslateY",
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isContentVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isContentVisible) DockCardAnimations.CONTENT_REVEAL_MS else DockCardAnimations.CONTENT_COLLAPSE_MS,
        ),
        label = "contentAlpha",
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
                rawDevice = device,
            )
            if (isPaired) paired.add(uiModel) else discovered.add(uiModel)
        }
        Pair(discovered, paired)
    }

    val devices = remember(devicesMap) { devicesMap.values.toList() }

    // Mock DeXStudios fallback removed for Phase 4.2 Parity

    val serverIp = devices.firstOrNull()?.ip ?: "127.0.0.1"
    val serverPort = devices.firstOrNull()?.info?.port ?: DeXPorts.HTTPS
    val serverIpPortText = "$serverIp:$serverPort"

    // Dedicated Live Screen Mirroring Window
    if (isMirroringActive) {
        val activeDeviceName = devices.firstOrNull()?.info?.alias ?: "Connected Phone"
        MirrorWindow(
            peerName = activeDeviceName,
            onClose = { isMirroringActive = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .graphicsLayer {
                translationY = menuTranslateY.toPx()
            },
    ) {
        // Top Actions Panel with DragPillHandle & Tactile QuickActionBar
        Box(
            modifier = Modifier.graphicsLayer {
                translationY = contentTranslateY.toPx()
                alpha = contentAlpha
            },
        ) {
            TopActionsPanel(
                controller = controller,
                isDndActive = isDndActive,
                onToggleDnd = { deviceConfig.dndEnabled = !isDndActive },
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
                statusTelemetryText = when {
                    isUploading -> "Transferring"

                    else ->
                        preferredTargetFp
                            ?.let { fp -> (discoveredList + pairedList).firstOrNull { it.fingerprint == fp }?.alias }
                            ?.let { "Ready - $it" }
                            ?: "Ready"
                },
                serverIpPort = serverIpPortText,
                showTelemetry = false, // WPF pnlAdbStatus is hidden by default (Height=0) until connected
            )
        }

        com.dexstudios.dex.window.components.ActiveTransferDashboard(
            modifier = Modifier.fillMaxWidth(),
        )

        val externalDragState = LocalExternalDragState.current

        val deviceDropTarget = remember(pairedList, discoveredList) {
            object : DragAndDropTarget {
                override fun onStarted(event: DragAndDropEvent) {
                    externalDragState.isExternalDragActive = true
                }

                override fun onEntered(event: DragAndDropEvent) {
                    externalDragState.isDeviceSectionHovered = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    externalDragState.isDeviceSectionHovered = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    externalDragState.isExternalDragActive = false
                    externalDragState.isDeviceSectionHovered = false
                }

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    externalDragState.isExternalDragActive = false
                    externalDragState.isDeviceSectionHovered = false
                    val transferable = event.awtTransferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<java.io.File>).orEmpty()
                        if (files.isNotEmpty()) {
                            val targetDevice = (pairedList + discoveredList).firstOrNull { it.isOnline }
                            if (targetDevice != null) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    fileSender.sendFiles(files, targetDevice.fingerprint)
                                }
                                return true
                            } else {
                                overlayManager.showToast(
                                    message = "No device connected to receive files",
                                    variant = ToastVariant.Warning,
                                )
                                return false
                            }
                        }
                    }
                    return false
                }
            }
        }

        val isDropZoneHovered = externalDragState.isDeviceSectionHovered
        val dropZoneScale by animateFloatAsState(
            targetValue = if (isDropZoneHovered) 1.03f else 1.0f,
            animationSpec = tween(durationMillis = 200, easing = DockCardPhysics.HoverEase),
            label = "deviceDropZoneScale",
        )
        val dropZoneBorderColor by animateColorAsState(
            targetValue = if (isDropZoneHovered) MaterialTheme.colorScheme.primary else Color.Transparent,
            animationSpec = tween(200),
            label = "deviceDropZoneBorder",
        )

        // Device List - occupies flexible viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .graphicsLayer {
                    translationY = contentTranslateY.toPx()
                    alpha = contentAlpha
                    scaleX = dropZoneScale
                    scaleY = dropZoneScale
                }
                .border(
                    width = if (isDropZoneHovered) 1.5.dp else 0.dp,
                    color = dropZoneBorderColor,
                    shape = RoundedCornerShape(16.dp),
                )
                .clip(RoundedCornerShape(16.dp))
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    },
                    target = deviceDropTarget,
                ),
        ) {
            DeviceListPanel(
                discoveredDevices = discoveredList,
                pairedDevices = pairedList,
                isPanelVisible = controller.isVisible,
                onPairDevice = { item ->
                    val selectedDevice = item.rawDevice ?: devices.find { it.info.fingerprint == item.fingerprint }
                    selectedDevice?.let { onPairDevice(it) }
                },
                onViewDeviceStatus = {
                    controller.expandPanel(ExpandedPanel.DeviceStatus)
                },
                onSendFile = { item ->
                    // Pin this device as the default drop target, then send picked files
                    // explicitly to it. The native chooser steals focus, so the modal
                    // guard keeps the dock card from auto-hiding mid-selection.
                    coroutineScope.launch(Dispatchers.IO) {
                        fileSender.setPreferredTarget(item.fingerprint)
                        val picked = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            runCatching {
                                controller.isModalDialogOpen = true
                                try {
                                    val holder = arrayOfNulls<List<java.io.File>>(1)
                                    java.awt.EventQueue.invokeAndWait {
                                        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Send files to ${item.alias}", java.awt.FileDialog.LOAD)
                                        dialog.isMultipleMode = true
                                        dialog.isVisible = true
                                        holder[0] = dialog.files.toList()
                                    }
                                    holder[0].orEmpty()
                                } finally {
                                    controller.isModalDialogOpen = false
                                }
                            }.getOrDefault(emptyList())
                        }
                        if (picked.isNotEmpty()) {
                            fileSender.sendFiles(picked, item.fingerprint)
                        }
                    }
                },
                onSendClipboard = { item ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            val text = clipboard.getData(DataFlavor.stringFlavor) as? String
                            if (text.isNullOrBlank()) {
                                Logger.i("Clipboard is empty or not text - nothing to push to ${item.alias}")
                                return@launch
                            }
                            val device = item.rawDevice
                            val ok = clientEngine.sendClipboard(
                                ip = item.ip,
                                port = device?.info?.port ?: DeXPorts.LOCALSEND_DEFAULT,
                                text = text,
                                targetFingerprint = device?.info?.fingerprint ?: item.fingerprint,
                                targetIdentityHash = device?.info?.identityHash,
                                targetGoogleSub = device?.info?.googleSub,
                            )
                            Logger.i(if (ok) "Pushed clipboard text to ${item.alias}" else "Failed to push clipboard to ${item.alias}")
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
                            null,
                        )
                    } catch (_: Exception) {}
                },
                onRenameDevice = {},
                onForgetDevice = { item ->
                    // Real revocation: drop the persisted pairing (fingerprint AND token),
                    // downgrade any live session, and tell the peer so it downgrades too.
                    coroutineScope.launch(Dispatchers.IO) {
                        com.dexstudios.dex.core.network.services.TrustRevocationService.revokeDevice(
                            fingerprint = item.fingerprint,
                            deviceConfig = deviceConfig,
                        )
                        Logger.i("Forgot device ${item.fingerprint}; trust revoked")
                    }
                },
            )
        }

        // Bottom Dock Panel (Profile avatar -> Settings, 2-stage Exit Engine)
        Box(
            modifier = Modifier.graphicsLayer {
                translationY = contentTranslateY.toPx()
                alpha = contentAlpha
            },
        ) {
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
                isMirroringActive = isMirroringActive,
                isPanelVisible = controller.isVisible,
            )
        }
    }
}
