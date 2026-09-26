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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.buttons.DeXCloseButton
import com.dexstudios.dex.core.designsystem.components.buttons.DeXCloseButtonSize
import com.dexstudios.dex.core.designsystem.components.overlay.ToastVariant
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_bolt
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_devices
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_file_download
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
 * Interactive Testing & Evaluation Lab for DeX Overlay & Notification Surfaces.
 * Allows live triggering and evaluation of component geometry, gestures, and motion kinematics.
 */
@Composable
fun OverlayTestingPlayground(overlayManager: OverlayManager, controller: DockedWindowStateController? = null, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
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
                text = "Overlay & Surface Test Lab",
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
            text = "Trigger live desktop overlay surfaces and connected device screens to evaluate kinematics:",
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

        // 3. Corner Message Toasts (MessageToast variants)
        TestActionButton(
            title = "3. Corner Message Toasts (All 5 Variants)",
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

        // 4. Fluid Notification Stack (FluidNotificationStack)
        TestActionButton(
            title = "4. Multi-Card Stack (Overlapping 8dp Peeks & Fan-Out)",
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

        // 5. High-Volume Flood (8+ Toasts)
        TestActionButton(
            title = "5. Flood 8 Toasts (Test 5-Card Cap & Backlog Pill)",
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

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Connected & Discovered Device Screens:",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // 6a. Phone Connected Screen
        TestActionButton(
            title = "Phone Connected Screen (Galaxy S24)",
            subtitle = "Surfaces floating card with live Phone 3D animation & telemetry",
            iconColor = primaryColor,
        ) {
            onClose()
            controller?.show()
            controller?.expandPanel(ExpandedPanel.DeviceStatus)
        }

        // 6b. Tablet Connected Screen
        TestActionButton(
            title = "Tablet Connected Screen (Galaxy Tab S9)",
            subtitle = "Surfaces floating card with live 3D Tablet animation",
            iconColor = primaryColor,
        ) {
            onClose()
            controller?.show()
            controller?.expandPanel(ExpandedPanel.DeviceStatusTablet)
        }

        // 6c. Laptop Connected Screen
        TestActionButton(
            title = "Laptop Connected Screen (Galaxy Book 4)",
            subtitle = "Surfaces floating card with live 3D Laptop opening animation",
            iconColor = primaryColor,
        ) {
            onClose()
            controller?.show()
            controller?.expandPanel(ExpandedPanel.DeviceStatusLaptop)
        }

        // 6d. Smartwatch Connected Screen
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
