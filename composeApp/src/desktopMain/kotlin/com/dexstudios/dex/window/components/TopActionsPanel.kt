package com.dexstudios.dex.window.components
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_check
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_clipboard
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.window.DockedWindowStateController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * TopActionsPanel:
 * 1. Top row: DragPillHandle (draggable handle, pin button, double-click reset)
 * 2. Middle row: Tactile QuickActionBar (62x48dp pills + collapsible danger close)
 * 3. Bottom row: 39dp Collapsible Status Bar Telemetry (IP:port + Copy IP button with feedback)
 */
@Composable
fun TopActionsPanel(
    controller: DockedWindowStateController,
    isDndActive: Boolean,
    onToggleDnd: () -> Unit,
    isMirroringActive: Boolean,
    onToggleMirror: () -> Unit,
    isTransfersActive: Boolean,
    onToggleTransfers: () -> Unit,
    isClipboardActive: Boolean,
    onToggleClipboard: () -> Unit,
    clipboardBadgeCount: Int = 0,
    statusTelemetryText: String = "Ready",
    serverIpPort: String = "",
    showTelemetry: Boolean = true,
    modifier: Modifier = Modifier,
    overlayManager: com.dexstudios.dex.overlay.OverlayManager = org.koin.compose.koinInject(),
) {
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Drag Pill & Pin Handle Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            DragPillHandle(
                controller = controller,
                modifier = Modifier.fillMaxWidth(),
            )

            OverlayTestingButton(
                overlayManager = overlayManager,
                controller = controller,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            )
        }

        // 2. Tactile Quick Actions Row (56x44dp Pills + Dynamic Danger Close)
        Box(
            modifier = Modifier.padding(bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            QuickActionBar(
                isDndActive = isDndActive,
                isMirroringActive = isMirroringActive,
                isTransfersActive = isTransfersActive,
                isClipboardActive = isClipboardActive,
                clipboardBadgeCount = clipboardBadgeCount,
                isPanelExpanded = controller.isExpanded,
                onToggleDnd = onToggleDnd,
                onToggleMirror = onToggleMirror,
                onToggleTransfers = onToggleTransfers,
                onToggleClipboard = onToggleClipboard,
                onCloseExpandedPanel = { controller.collapsePanel() },
            )
        }

        // 3. Status Bar Telemetry (Collapsible 39dp Row)
        AnimatedVisibility(
            visible = showTelemetry,
            enter = expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val displayText = if (serverIpPort.isNotBlank()) {
                        "$statusTelemetryText ($serverIpPort)"
                    } else {
                        statusTelemetryText
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = displayText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                        )

                        if (serverIpPort.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        try {
                                            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                                StringSelection(serverIpPort),
                                                null,
                                            )
                                        } catch (_: Exception) {}
                                        isCopied = true
                                        scope.launch {
                                            delay(1500)
                                            isCopied = false
                                        }
                                    }
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = if (isCopied) painterResource(Res.drawable.ic_fluent_check) else painterResource(Res.drawable.ic_fluent_clipboard),
                                    contentDescription = "Copy IP",
                                    tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
