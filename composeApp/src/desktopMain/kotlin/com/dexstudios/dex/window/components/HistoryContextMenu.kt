package com.dexstudios.dex.window.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.frostedSurface
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_article
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_delete
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_rotate
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_send
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import org.jetbrains.compose.resources.painterResource

/**
 * Context menu for an individual File or Folder item in History view.
 */
@Composable
fun HistoryItemContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    item: ExplorerFileItem,
    isPhoneConnected: Boolean,
    selectedCount: Int = 1,
    offset: DpOffset = DpOffset.Zero,
    onOpen: () -> Unit,
    onOpenLocation: () -> Unit,
    onSendToPhone: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true,
        ),
        modifier = Modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Color.Black.copy(alpha = 0.48f),
                ambientColor = Color.Black.copy(alpha = 0.26f),
            )
            .frostedSurface(
                shape = RoundedCornerShape(18.dp),
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                opacity = 1.0f,
            )
            .bubbleFluidity(targetScale = 0.98f, pullFactor = 0.04f),
    ) {
        // 1. Open
        ContextMenuItemRow(
            icon = painterResource(if (item.isDirectory) Res.drawable.ic_fluent_folder else Res.drawable.ic_fluent_article),
            title = if (selectedCount > 1) {
                "Open ($selectedCount items)"
            } else if (item.isDirectory) {
                "Open Folder"
            } else {
                "Open"
            },
            shortcut = if (selectedCount <= 1) "Enter" else null,
            onClick = {
                onDismissRequest()
                onOpen()
            },
        )

        // 2. Open Location (shown for single item)
        if (selectedCount <= 1) {
            ContextMenuItemRow(
                icon = painterResource(Res.drawable.ic_fluent_folder),
                title = "Open Location",
                onClick = {
                    onDismissRequest()
                    onOpenLocation()
                },
            )
        }

        // 3. Send to Phone
        ContextMenuItemRow(
            icon = painterResource(Res.drawable.ic_fluent_send),
            title = if (selectedCount > 1) {
                "Send $selectedCount items to Phone"
            } else if (item.isDirectory) {
                "Send Folder to Phone"
            } else {
                "Send to Phone"
            },
            enabled = isPhoneConnected,
            onClick = {
                onDismissRequest()
                onSendToPhone()
            },
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )

        // 4. Delete (Destructive Accent with Del shortcut)
        ContextMenuItemRow(
            icon = painterResource(Res.drawable.ic_fluent_delete),
            title = if (selectedCount > 1) "Delete ($selectedCount items)" else "Delete",
            shortcut = "Del",
            isDestructive = true,
            onClick = {
                onDismissRequest()
                onDelete()
            },
        )
    }
}

/**
 * Context menu for right-clicking the empty background canvas of the History grid.
 */
@Composable
fun HistoryCanvasContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    onOpenDownloadsFolder: () -> Unit,
    onRefresh: () -> Unit,
    onClearAllHistory: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true,
        ),
        modifier = Modifier
            .widthIn(min = 200.dp, max = 250.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Color.Black.copy(alpha = 0.48f),
                ambientColor = Color.Black.copy(alpha = 0.26f),
            )
            .frostedSurface(
                shape = RoundedCornerShape(18.dp),
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                opacity = 1.0f,
            )
            .bubbleFluidity(targetScale = 0.98f, pullFactor = 0.04f),
    ) {
        ContextMenuItemRow(
            icon = painterResource(Res.drawable.ic_fluent_folder),
            title = "Open Downloads Folder",
            onClick = {
                onDismissRequest()
                onOpenDownloadsFolder()
            },
        )

        ContextMenuItemRow(
            icon = painterResource(Res.drawable.ic_fluent_rotate),
            title = "Refresh Listing",
            shortcut = "F5",
            onClick = {
                onDismissRequest()
                onRefresh()
            },
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )

        ContextMenuItemRow(
            icon = painterResource(Res.drawable.ic_fluent_delete),
            title = "Clear All History",
            isDestructive = true,
            onClick = {
                onDismissRequest()
                onClearAllHistory()
            },
        )
    }
}

/**
 * Individual row within custom styled context menus.
 */
@Composable
private fun ContextMenuItemRow(icon: Painter, title: String, modifier: Modifier = Modifier, shortcut: String? = null, enabled: Boolean = true, isDestructive: Boolean = false, onClick: () -> Unit) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    val itemInteraction = remember { MutableInteractionSource() }
    val isHovered by itemInteraction.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered && enabled) 1.03f else 1.0f,
        animationSpec = tween(350, easing = DockCardPhysics.HoverEase),
        label = "menuItemScale",
    )
    val transX by animateDpAsState(
        targetValue = if (isHovered && enabled) 3.dp else 0.dp,
        animationSpec = tween(350, easing = DockCardPhysics.HoverEase),
        label = "menuItemTransX",
    )

    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = transX.toPx()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!shortcut.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = shortcut,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f) else contentColor,
                    )
                }
            }
        },
        onClick = onClick,
        enabled = enabled,
        interactionSource = itemInteraction,
        modifier = modifier.bubbleFluidity(targetScale = 0.97f, pullFactor = 0.05f),
        colors = MenuDefaults.itemColors(
            textColor = contentColor,
            disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        ),
    )
}
