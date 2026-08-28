package com.dexstudios.dex.window.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.mirror.toImageBitmap
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

/**
 * 100x115dp File & Folder Grid Card with hover lift, micro-thumbnail decoding,
 * multi-selection modifier click handling, native OS Drag-and-Drop, and History context menu.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun FileGridItemCard(
    item: ExplorerFileItem,
    isSelected: Boolean,
    mode: ExplorerMode = ExplorerMode.History,
    isPhoneConnected: Boolean = false,
    selectedCount: Int = 1,
    selectedFilesProvider: (() -> List<File>)? = null,
    onPositioned: ((id: String, boundsInRoot: Rect) -> Unit)? = null,
    onClick: (isCtrlOrMeta: Boolean, isShift: Boolean) -> Unit,
    onSecondaryClick: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onOpenLocation: (() -> Unit)? = null,
    onSendToPhone: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var isContextMenuOpen by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.05f else 1.0f,
        animationSpec = DockCardAnimations.HoverSpec,
        label = "itemScale",
    )

    // Decode Base64 micro-thumbnail off the UI thread
    val thumbnailBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.thumbBase64) {
        val raw = item.thumbBase64
        if (raw.isNullOrBlank()) {
            value = null
        } else {
            value = withContext(Dispatchers.IO) {
                runCatching { Base64.getDecoder().decode(raw).toImageBitmap() }.getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .onGloballyPositioned { coordinates ->
                onPositioned?.invoke(item.id, coordinates.boundsInRoot())
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> if (MaterialTheme.colorScheme.surface == Color.White) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    isHovered -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                },
            )
            .hoverable(interactionSource)
            .dragAndDropSource {
                if (mode == ExplorerMode.History && !item.isAddFolderButton) {
                    val filesToDrag = if (isSelected && selectedFilesProvider != null) {
                        selectedFilesProvider()
                    } else {
                        val f = File(item.path)
                        if (f.exists()) listOf(f) else emptyList()
                    }
                    if (filesToDrag.isNotEmpty()) {
                        DragAndDropTransferData(
                            transferable = DragAndDropTransferable(FileListTransferable(filesToDrag)),
                            supportedActions = listOf(DragAndDropTransferAction.Copy),
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            .pointerInput(item.id, mode, isSelected) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Press) {
                            val change = event.changes.firstOrNull() ?: continue
                            if (event.button == PointerButton.Secondary) {
                                if (mode == ExplorerMode.History && !item.isAddFolderButton) {
                                    change.consume()
                                    menuOffset = DpOffset(change.position.x.toDp(), change.position.y.toDp())
                                    onSecondaryClick?.invoke()
                                    isContextMenuOpen = true
                                }
                            } else if (event.button == PointerButton.Primary) {
                                change.consume()
                                val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                                val isShift = event.keyboardModifiers.isShiftPressed
                                onClick(isCtrl, isShift)
                            }
                        }
                    }
                }
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (mode == ExplorerMode.History && !item.isAddFolderButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(menuOffset.x, menuOffset.y)
                    .size(0.dp),
            ) {
                HistoryItemContextMenu(
                    expanded = isContextMenuOpen,
                    onDismissRequest = { isContextMenuOpen = false },
                    offset = DpOffset.Zero,
                    item = item,
                    isPhoneConnected = isPhoneConnected,
                    selectedCount = selectedCount,
                    onOpen = {
                        isContextMenuOpen = false
                        onOpen?.invoke()
                    },
                    onOpenLocation = {
                        isContextMenuOpen = false
                        onOpenLocation?.invoke()
                    },
                    onSendToPhone = {
                        isContextMenuOpen = false
                        onSendToPhone?.invoke()
                    },
                    onDelete = {
                        isContextMenuOpen = false
                        onDelete?.invoke()
                    },
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            // 56x56dp Icon or Micro-Thumbnail Glyph
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                val isMedia = item.name.endsWith(".jpg", true) || item.name.endsWith(".png", true) || item.name.endsWith(".jpeg", true)
                val bitmap = thumbnailBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (isMedia && !item.isDirectory) {
                    val coilUri = remember(item.path) {
                        val file = java.io.File(item.path)
                        if (file.isAbsolute) file.toURI().toString() else item.path
                    }
                    coil3.compose.SubcomposeAsyncImage(
                        model = coilUri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = getFileIcon(item),
                        contentDescription = item.name,
                        tint = getFileIconColor(item),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // File Name Label
            Text(
                text = item.name,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )

            // File Size or Timestamp Sub-label (Tightened spacing directly below name)
            Text(
                text = if (item.isDirectory) (if (item.isAddFolderButton) "SAF Picker" else "Folder") else formatFileSize(item.size),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}
