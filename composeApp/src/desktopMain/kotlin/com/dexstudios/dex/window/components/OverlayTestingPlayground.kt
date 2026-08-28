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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.overlay.BannerMorphState
import com.dexstudios.dex.core.designsystem.components.overlay.ToastVariant
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_bolt
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_devices
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_file_download
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_notifications
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_photo
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_share
import com.dexstudios.dex.core.designsystem.generated.resources.joe_avatar
import com.dexstudios.dex.core.designsystem.generated.resources.wallpaper_laptop
import com.dexstudios.dex.overlay.OverlayManager
import com.dexstudios.dex.window.DockedWindowStateController
import com.dexstudios.dex.window.ExpandedPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * Temporary Testing & Review Playground for the DeX Fluid Overlay & Notification System.
 */
@Composable
fun OverlayTestingButton(modifier: Modifier = Modifier, overlayManager: OverlayManager, controller: DockedWindowStateController? = null) {
    val coroutineScope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Floating action pill in top bar / dock area
        Row(
            modifier = Modifier
                .bubbleFluidity()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { isExpanded = !isExpanded },
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_fluent_notifications),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Overlays",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        // Overlay Playground Panel Dropdown
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .padding(top = 36.dp)
                    .width(360.dp)
                    .height(440.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                    .padding(16.dp),
            ) {
                OverlayTestingPlayground(
                    overlayManager = overlayManager,
                    controller = controller,
                    onClose = { isExpanded = false },
                )
            }
        }
    }
}

@Composable
fun OverlayTestingPlayground(overlayManager: OverlayManager, controller: DockedWindowStateController? = null, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Overlay Test Lab",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Close",
                fontSize = 12.sp,
                color = primaryColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onClose() },
            )
        }

        Text(
            text = "Trigger real on-screen surfaces to test physics, hover fan-out, dynamic morphing, and gestures:",
            fontSize = 12.sp,
            color = onSurfaceVariantColor,
            lineHeight = 16.sp,
        )

        // 1. Live File Transfer Banner (Dynamic Island)
        TestActionButton(
            title = "1. Live Transfer Banner (Dynamic Island)",
            subtitle = "Tap to test compact pill & click-to-expand HUD",
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
                initialMorphState = BannerMorphState.Compact,
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

        // 2. AirDrop Modal Alert
        TestActionButton(
            title = "2. AirDrop Alert Dialog (Photo Share)",
            subtitle = "Modal card with preview image & Decline/Accept buttons",
            iconColor = primaryColor,
        ) {
            var isLoading by mutableStateOf(false)
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
                    overlayManager.showToast(
                        message = "AirDrop declined",
                        variant = ToastVariant.Info,
                    )
                },
                onPositiveAction = {
                    overlayManager.showToast(
                        message = "Receiving 23 photos...",
                        variant = ToastVariant.Success,
                    )
                },
            )
        }

        // 3. Corner Toasts
        TestActionButton(
            title = "3. Corner Message Toasts",
            subtitle = "Dispatches Success, Error, and Progress toasts to corner",
            iconColor = tertiaryColor,
        ) {
            overlayManager.showToast(
                message = "Clipboard synced from iPhone 16 Pro",
                variant = ToastVariant.Success,
            )
            coroutineScope.launch {
                delay(600)
                overlayManager.showToast(
                    message = "Sending 'vacation.mp4' • 65%",
                    variant = ToastVariant.Progress,
                    actionText = "Cancel",
                    onActionClick = {
                        overlayManager.showToast("Transfer cancelled", ToastVariant.Warning)
                    },
                )
            }
        }

        // 4a. Phone Connected Screen
        TestActionButton(
            title = "4a. Phone Connected Screen (Galaxy S24)",
            subtitle = "Surfaces floating card with live Phone 3D animation & telemetry",
            iconColor = primaryColor,
        ) {
            onClose()
            controller?.show()
            controller?.expandPanel(ExpandedPanel.DeviceStatus)
        }

        // 4b. Tablet Connected Screen
        TestActionButton(
            title = "4b. Tablet Connected Screen (Galaxy Tab S9)",
            subtitle = "Surfaces floating card with live 3D Tablet animation",
            iconColor = primaryColor,
        ) {
            onClose()
            controller?.show()
            controller?.expandPanel(ExpandedPanel.DeviceStatusTablet)
        }

        // 4c. Laptop Connected Screen
        TestActionButton(
            title = "4c. Laptop Connected Screen (Galaxy Book 4)",
            subtitle = "Surfaces floating card with live 3D Laptop opening animation",
            iconColor = primaryColor,
        ) {
            onClose()
            controller?.show()
            controller?.expandPanel(ExpandedPanel.DeviceStatusLaptop)
        }

        // 4d. Smartwatch Connected Screen
        TestActionButton(
            title = "4d. Smartwatch Connected Screen (Galaxy Watch 6)",
            subtitle = "Surfaces floating card with live 3D Watch rotating animation",
            iconColor = primaryColor,
        ) {
            onClose()
            controller?.show()
            controller?.expandPanel(ExpandedPanel.DeviceStatusWatch)
        }

        // 6. Multi-Card Stack (4 Items Burst)
        TestActionButton(
            title = "6. Multi-Card Stack (Test 8dp Peeks & Hover Fan-Out)",
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

        // 6. High-Volume Flood (8+ Toasts)
        TestActionButton(
            title = "6. Flood 8 Toasts (Test 5-Card Cap & Backlog Pill)",
            subtitle = "Simulates batch file arrival to test cap + '+N more • Clear All'",
            iconColor = tertiaryColor,
        ) {
            for (i in 1..8) {
                overlayManager.showToast(
                    message = "Photo $i of 8 transferred successfully",
                    variant = ToastVariant.Success,
                    autoDismissTimeoutMs = 12_000L,
                )
            }
        }

        // 7. Simulate Live File Transfer
        TestActionButton(
            title = "7. Simulate Live File Transfer",
            subtitle = "Spawns dynamic banner with progress, then AirDrop completion card",
            iconColor = primaryColor,
        ) {
            coroutineScope.launch {
                val fileName = "vacation_2026_hawaii.jpg"
                var progress = 0.0f
                val id = overlayManager.showBanner(
                    title = fileName,
                    subtitle = "0%",
                    badgeText = "Download",
                    iconResource = com.dexstudios.dex.core.designsystem.icons.DeXIcons.ArrowDownloadArrow,
                    progress = progress,
                    autoDismissTimeoutMs = null,
                )

                while (progress < 1.0f) {
                    delay(300)
                    progress += 0.15f
                    if (progress > 1.0f) progress = 1.0f

                    val percent = (progress * 100).toInt()
                    val speed = if (percent < 100) "45.2 MB/s • $percent%" else "Complete"

                    overlayManager.updateBanner(
                        id = id,
                        title = fileName,
                        subtitle = speed,
                        progress = progress,
                    )
                }

                delay(500)
                overlayManager.dismiss(id)

                overlayManager.showAlert(
                    title = "Received 'vacation_2026_hawaii.jpg'",
                    message = "Saved to Downloads/DeX",
                    iconResource = com.dexstudios.dex.core.designsystem.icons.DeXIcons.FileDownload,
                    previewContent = {
                        androidx.compose.foundation.Image(
                            painter = org.jetbrains.compose.resources.painterResource(
                                com.dexstudios.dex.core.designsystem.generated.resources.Res.drawable.wallpaper_laptop,
                            ),
                            contentDescription = "Simulated Thumbnail",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    positiveButtonText = "Open",
                    negativeButtonText = "Folder",
                    onPositiveAction = {},
                    onNegativeAction = {},
                    playSound = true,
                )
            }
        }

        // 8. Dismiss All
        TestActionButton(
            title = "Clear All Active Notifications",
            subtitle = "Dismisses all active banners and corner toasts",
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            overlayManager.dismissAll()
        }
    }
}

@Composable
private fun TestActionButton(title: String, subtitle: String, iconColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bubbleFluidity(targetScale = 0.98f, pullFactor = 0.03f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
