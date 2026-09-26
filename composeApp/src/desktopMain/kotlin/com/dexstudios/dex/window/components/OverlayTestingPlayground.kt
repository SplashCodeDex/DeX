package com.dexstudios.dex.window.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.buttons.DeXCloseButton
import com.dexstudios.dex.core.designsystem.components.buttons.DeXCloseButtonSize
import com.dexstudios.dex.core.designsystem.components.overlay.ConfirmationPopup
import com.dexstudios.dex.core.designsystem.components.overlay.ToastVariant
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_bolt
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_devices
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_file_download
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_share
import com.dexstudios.dex.core.designsystem.generated.resources.joe_avatar
import com.dexstudios.dex.core.designsystem.generated.resources.wallpaper_laptop
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.TransferStateMonitor
import com.dexstudios.dex.overlay.OverlayManager
import com.dexstudios.dex.window.DockedWindowStateController
import com.dexstudios.dex.window.ExpandedPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * Interactive Testing & Evaluation Lab for all 11 DeX Overlay and Notification Surfaces.
 * Allows live triggering and evaluation of each component's geometry, gestures, and motion kinematics.
 */
@Composable
fun OverlayTestingPlayground(overlayManager: OverlayManager, controller: DockedWindowStateController? = null, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val clientEngine: ClientEngine = koinInject()
    val discoveryEngine: DiscoveryEngine = koinInject()

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    // In-Lab Modal Previews
    var showConfirmationPopupPreview by remember { mutableStateOf(false) }
    var showInboundPairingPreview by remember { mutableStateOf(false) }
    var showQuickLookModalPreview by remember { mutableStateOf(false) }
    var showAdbPickerPreview by remember { mutableStateOf(false) }
    var showPullProgressPreview by remember { mutableStateOf(false) }
    var isTransferDashboardActive by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Overlay Test Lab (11 Surfaces)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DeXCloseButton(
                    size = DeXCloseButtonSize.Small,
                    contentDescription = "Close",
                    onClick = onClose,
                )
            }

            Text(
                text = "Trigger each of the 11 surfaces live to evaluate feel, layout, and morphing transitions:",
                fontSize = 12.sp,
                color = onSurfaceVariantColor,
                lineHeight = 16.sp,
            )

            // 1. Dynamic Island Banner (NotificationBanner)
            TestActionButton(
                title = "1. Live Transfer Banner (Dynamic Island)",
                subtitle = "Natural entry bloom (Expanded) -> auto-contracts to compact pill -> tap to toggle",
                iconColor = primaryColor,
            ) {
                overlayManager.showBanner(
                    title = "vacation_2026.mp4",
                    subtitle = "1.4 / 2.1 GB • 48 MB/s",
                    badgeText = "Galaxy S24 Ultra • 12s left",
                    iconResource = Res.drawable.ic_fluent_file_download,
                    iconBackgroundColor = primaryContainerColor,
                    iconTint = onPrimaryContainerColor,
                    progress = 0.65f,
                    trailingPreview = {
                        Image(
                            painter = painterResource(Res.drawable.wallpaper_laptop),
                            contentDescription = "Video Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    onActionClick = {
                        overlayManager.showToast(
                            message = "Transfer cancelled",
                            variant = ToastVariant.Warning,
                        )
                    },
                )
            }

            // 2. AirDrop Modal Alert (AlertDialog)
            TestActionButton(
                title = "2. AirDrop Alert Dialog (Photo Share)",
                subtitle = "Modal card with preview image & Decline/Accept action buttons",
                iconColor = primaryColor,
            ) {
                overlayManager.showAlert(
                    title = "AirDrop",
                    message = "Danny Lopez would like to share 23 photos",
                    iconResource = Res.drawable.ic_fluent_share,
                    iconTint = primaryColor,
                    badgeResource = Res.drawable.joe_avatar,
                    previewContent = {
                        Image(
                            painter = painterResource(Res.drawable.wallpaper_laptop),
                            contentDescription = "Shared Photos",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    negativeButtonText = "Decline",
                    positiveButtonText = "Accept",
                    onNegativeAction = {
                        overlayManager.showToast("AirDrop declined", ToastVariant.Info)
                    },
                    onPositiveAction = {
                        overlayManager.showToast("Receiving 23 photos...", ToastVariant.Success)
                    },
                )
            }

            // 3. Stacked Screen Overlay (StackedScreen)
            TestActionButton(
                title = "3. Stacked Screen Overlay (420x560dp)",
                subtitle = "Full-content stacked screen with iOS pull-down drag handle and scrollable body",
                iconColor = secondaryColor,
            ) {
                overlayManager.pushStackedScreen(
                    title = "File Transfer Inspection",
                    subtitle = "Session #48291 • 5 Files",
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Detailed Transfer Manifest",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        for (i in 1..4) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("payload_chunk_00$i.bin", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Verified", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // 4. Corner Message Toasts (MessageToast variants)
            TestActionButton(
                title = "4. Corner Message Toasts (All 5 Variants)",
                subtitle = "Dispatches Info, Success, Warning, Error, and Progress toasts to corner",
                iconColor = tertiaryColor,
            ) {
                overlayManager.showToast(
                    message = "Info: Device discovered nearby",
                    variant = ToastVariant.Info,
                )
                coroutineScope.launch {
                    delay(350)
                    overlayManager.showToast(
                        message = "Success: Clipboard synced from phone",
                        variant = ToastVariant.Success,
                    )
                    delay(350)
                    overlayManager.showToast(
                        message = "Warning: Wi-Fi signal weak (18 MB/s)",
                        variant = ToastVariant.Warning,
                    )
                    delay(350)
                    overlayManager.showToast(
                        message = "Error: Transfer failed (Connection timed out)",
                        variant = ToastVariant.Error,
                    )
                    delay(350)
                    overlayManager.showToast(
                        message = "Progress: Pulling 'camera_dump.zip' • 68%",
                        variant = ToastVariant.Progress,
                        progress = 0.68f,
                        actionText = "Cancel",
                        onActionClick = { overlayManager.showToast("Cancelled", ToastVariant.Warning) },
                    )
                }
            }

            // 5. Fluid Notification Stack (FluidNotificationStack)
            TestActionButton(
                title = "5. Multi-Card Stack (Overlapping 8dp Peeks & Fan-Out)",
                subtitle = "Pushes 4 cards to test Apple overlapping stack & hover expand",
                iconColor = secondaryColor,
            ) {
                overlayManager.showBanner(
                    title = "1. File Download Complete",
                    subtitle = "vacation_2026.mp4 saved to Downloads",
                    iconResource = Res.drawable.ic_fluent_file_download,
                    autoDismissTimeoutMs = 15_000L,
                )
                overlayManager.showBanner(
                    title = "2. Device Connected",
                    subtitle = "Galaxy S24 Ultra (Wi-Fi 6)",
                    iconResource = Res.drawable.ic_fluent_devices,
                    autoDismissTimeoutMs = 15_000L,
                )
                overlayManager.showBanner(
                    title = "3. Clipboard Synced",
                    subtitle = "Copied 120 chars from Phone",
                    iconResource = Res.drawable.ic_fluent_share,
                    autoDismissTimeoutMs = 15_000L,
                )
                overlayManager.showBanner(
                    title = "4. DeX System Ready",
                    subtitle = "High-speed tunnel active on port 52400",
                    iconResource = Res.drawable.ic_fluent_bolt,
                    autoDismissTimeoutMs = 15_000L,
                )
            }

            // 6. Confirmation Popup (ConfirmationPopup)
            TestActionButton(
                title = "6. Destructive Confirmation Popup",
                subtitle = "Modal confirmation prompt used in File Explorer for deletions & clear history",
                iconColor = errorColor,
            ) {
                showConfirmationPopupPreview = true
            }

            // 7. Inbound PIN Pairing Dialog (InboundPairingCard)
            TestActionButton(
                title = "7. Inbound PIN Pairing Dialog",
                subtitle = "Centered modal card with 5-digit PIN input boxes and countdown timer",
                iconColor = primaryColor,
            ) {
                showInboundPairingPreview = true
            }

            // 8. Quick Look File Preview Modal (QuickLookModal)
            TestActionButton(
                title = "8. Quick Look File Preview Modal",
                subtitle = "macOS Quick Look modal dialog with image/text viewer and metadata breakdown",
                iconColor = tertiaryColor,
            ) {
                showQuickLookModalPreview = true
            }

            // 9. ADB Device Picker Dialog (AdbDevicePickerDialog)
            TestActionButton(
                title = "9. ADB Device Picker Dialog",
                subtitle = "Discovered network device chooser dialog for ADB connect",
                iconColor = primaryColor,
            ) {
                showAdbPickerPreview = true
            }

            // 10. Pull Progress Dock Toast (PullProgressDock)
            TestActionButton(
                title = if (showPullProgressPreview) "10. Pull Progress Dock Toast (Showing)" else "10. Pull Progress Dock Toast",
                subtitle = if (showPullProgressPreview) "Tap to hide preview" else "Floating transfer progress toast pinned at bottom of File Explorer",
                iconColor = if (showPullProgressPreview) primaryColor else onSurfaceVariantColor,
            ) {
                showPullProgressPreview = !showPullProgressPreview
            }

            if (showPullProgressPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PullProgressDock(
                        clientEngine = clientEngine,
                        onCancel = { showPullProgressPreview = false },
                    )
                }
            }

            // 11. Active Transfer Dashboard (ActiveTransferDashboard)
            TestActionButton(
                title = if (isTransferDashboardActive) "11. Active Transfer Dashboard (Active Above Dock)" else "11. Active Transfer Dashboard",
                subtitle = if (isTransferDashboardActive) "Tap to dismiss active transfer dashboard" else "Spawns active transfer in TransferStateMonitor (appears right above main dock)",
                iconColor = if (isTransferDashboardActive) errorColor else primaryColor,
            ) {
                if (isTransferDashboardActive) {
                    TransferStateMonitor.removeSession("lab_transfer_test")
                    isTransferDashboardActive = false
                } else {
                    TransferStateMonitor.updateIncomingProgress(
                        sessionId = "lab_transfer_test",
                        alias = "Pixel 9 Pro",
                        totalFiles = 4,
                        filesReceived = 2,
                        isComplete = false,
                        bytesReceived = 120_000_000L,
                        totalBytes = 240_000_000L,
                        speedBps = 28_000_000L,
                    )
                    isTransferDashboardActive = true
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Connected & Discovered Device Screens:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 12a. Phone Connected Screen
            TestActionButton(
                title = "Phone Connected Screen (Galaxy S24)",
                subtitle = "Surfaces floating card with live Phone 3D animation & telemetry",
                iconColor = primaryColor,
            ) {
                onClose()
                controller?.show()
                controller?.expandPanel(ExpandedPanel.DeviceStatus)
            }

            // 12b. Tablet Connected Screen
            TestActionButton(
                title = "Tablet Connected Screen (Galaxy Tab S9)",
                subtitle = "Surfaces floating card with live 3D Tablet animation",
                iconColor = primaryColor,
            ) {
                onClose()
                controller?.show()
                controller?.expandPanel(ExpandedPanel.DeviceStatusTablet)
            }

            // 12c. Laptop Connected Screen
            TestActionButton(
                title = "Laptop Connected Screen (Galaxy Book 4)",
                subtitle = "Surfaces floating card with live 3D Laptop opening animation",
                iconColor = primaryColor,
            ) {
                onClose()
                controller?.show()
                controller?.expandPanel(ExpandedPanel.DeviceStatusLaptop)
            }

            // 12d. Smartwatch Connected Screen
            TestActionButton(
                title = "Smartwatch Connected Screen (Galaxy Watch 6)",
                subtitle = "Surfaces floating card with live 3D Watch rotating animation",
                iconColor = primaryColor,
            ) {
                onClose()
                controller?.show()
                controller?.expandPanel(ExpandedPanel.DeviceStatusWatch)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dismiss All Action
            TestActionButton(
                title = "Clear All Active Notifications",
                subtitle = "Dismisses all active banners, toasts, and resets active transfer session",
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                overlayManager.dismissAll()
                if (isTransferDashboardActive) {
                    TransferStateMonitor.removeSession("lab_transfer_test")
                    isTransferDashboardActive = false
                }
                showPullProgressPreview = false
            }
        }

        // =====================================================================
        // Modal Preview Scrims for In-App Dialog Surfaces (6, 7, 8, 9)
        // =====================================================================

        // 6. Confirmation Popup Modal
        if (showConfirmationPopupPreview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .zIndex(99f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showConfirmationPopupPreview = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ConfirmationPopup(
                    title = "Clear Transfer History",
                    message = "Are you sure you want to clear all transfer history logs? Your physical files on disk will not be deleted.",
                    confirmButtonText = "Clear History",
                    cancelButtonText = "Cancel",
                    isDestructive = true,
                    onConfirm = {
                        showConfirmationPopupPreview = false
                        overlayManager.showToast("History cleared", ToastVariant.Success)
                    },
                    onCancel = { showConfirmationPopupPreview = false },
                    onDismiss = { showConfirmationPopupPreview = false },
                )
            }
        }

        // 7. Inbound Pairing Dialog Modal
        if (showInboundPairingPreview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .zIndex(99f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showInboundPairingPreview = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                InboundPairingCard(
                    alias = "Pixel 9 Pro",
                    deadlineElapsedMs = System.currentTimeMillis() + 60_000L,
                    onPinEntered = { pin ->
                        showInboundPairingPreview = false
                        overlayManager.showToast("PIN Entered: $pin", ToastVariant.Success)
                    },
                    onCancel = { showInboundPairingPreview = false },
                )
            }
        }

        // 8. Quick Look File Preview Modal
        if (showQuickLookModalPreview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .zIndex(99f),
                contentAlignment = Alignment.Center,
            ) {
                QuickLookModal(
                    item = ExplorerFileItem(
                        id = "lab_sample_preview",
                        name = "wallpaper_laptop.webp",
                        path = "",
                        size = 22078L,
                        isDirectory = false,
                        timestamp = System.currentTimeMillis(),
                    ),
                    currentIndex = 0,
                    totalCount = 1,
                    isPhoneConnected = true,
                    onDismiss = { showQuickLookModalPreview = false },
                    onOpenNative = { showQuickLookModalPreview = false },
                    onOpenLocation = { showQuickLookModalPreview = false },
                )
            }
        }

        // 9. ADB Device Picker Dialog
        if (showAdbPickerPreview) {
            val liveDiscovered = discoveryEngine.devices.collectAsState().value.values.toList()
            val sampleDevices = if (liveDiscovered.isNotEmpty()) {
                liveDiscovered
            } else {
                listOf(
                    DiscoveredDevice(
                        ip = "192.168.1.105",
                        info = RegisterDto(
                            alias = "Pixel 9 Pro",
                            version = "1.0",
                            deviceModel = "Pixel 9 Pro",
                            deviceType = "phone",
                            fingerprint = "lab-fp-1",
                            port = 53317,
                            protocol = "v1",
                            download = true,
                        ),
                    ),
                )
            }
            AdbDevicePickerDialog(
                devices = sampleDevices,
                onDismiss = { showAdbPickerPreview = false },
                onPick = { device ->
                    showAdbPickerPreview = false
                    overlayManager.showToast("Selected: ${device.info.alias} (${device.ip})", ToastVariant.Info)
                },
            )
        }
    }
}

@Composable
private fun TestActionButton(title: String, subtitle: String, iconColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bubbleFluidity(targetScale = 0.98f, pullFactor = 0.03f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(iconColor),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
