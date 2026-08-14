package com.dexstudios.dex.ui.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dexstudios.dex.R
import com.dexstudios.dex.network.TransferHistory
import com.dexstudios.dex.network.TransferRecord
import com.dexstudios.dex.ui.icons.MaterialSymbols as DeXIcons
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.state.TopAppBarState
import com.dexstudios.dex.ui.state.HistoryDirection
import com.dexstudios.dex.ui.state.HistoryType
import com.dexstudios.dex.ui.state.HistorySort
import com.dexstudios.dex.ui.state.HistoryViewMode
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val allItems by TransferHistory.items.collectAsStateWithLifecycle()
    val search = TopAppBarState.searchQuery
    val dirFilter = TopAppBarState.historyDirectionFilter
    val typeFilter = TopAppBarState.historyTypeFilter
    val sortOrder = TopAppBarState.historySortOrder
    val viewMode = TopAppBarState.historyViewMode

    val items = remember(allItems, search, dirFilter, typeFilter, sortOrder) {
        allItems.asSequence()
            .filter { it.name.contains(search, ignoreCase = true) }
            .filter {
                when (dirFilter) {
                    HistoryDirection.ALL -> true
                    HistoryDirection.SENT -> it.direction == "sent"
                    HistoryDirection.RECEIVED -> it.direction == "received"
                }
            }
            .filter {
                if (typeFilter == HistoryType.ALL) true
                else getHistoryType(it.name) == typeFilter
            }
            .sortedWith { a, b ->
                when (sortOrder) {
                    HistorySort.DATE_DESC -> b.timestamp.compareTo(a.timestamp)
                    HistorySort.SIZE_DESC -> b.size.compareTo(a.size)
                    HistorySort.NAME_ASC -> a.name.compareTo(b.name, ignoreCase = true)
                }
            }
            .toList()
    }

    val groupedItems = remember(items) {
        items.groupBy { getDateGroupLabel(it.timestamp) }
    }
    val groupOrder = listOf("Today", "Yesterday", "This Week", "Older")

    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    val selectionActive = selectedIds.isNotEmpty()
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var lightboxRecord by remember { mutableStateOf<TransferRecord?>(null) }

    LaunchedEffect(Unit) {
        TransferHistory.refresh(context)
    }

    val contentBackdrop = rememberLayerBackdrop()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val gridState = rememberLazyGridState()

    val scrollOffset by remember(viewMode) {
        derivedStateOf {
            val index = if (viewMode == HistoryViewMode.LIST) listState.firstVisibleItemIndex else gridState.firstVisibleItemIndex
            val offset = if (viewMode == HistoryViewMode.LIST) listState.firstVisibleItemScrollOffset else gridState.firstVisibleItemScrollOffset
            if (index == 0) {
                offset.toFloat()
            } else {
                500f
            }
        }
    }
    val collapseThreshold = 200f
    val scrollFactor = (scrollOffset / collapseThreshold).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )

            // Dynamic Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -scrollOffset * 0.5f
                        alpha = 1f - scrollFactor
                    }
                    .padding(top = statusBarHeight + 84.dp)
                    .zIndex(2f)
            ) {
                if (selectionActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(DeXIcons.Close, contentDescription = "Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedIds.size} Selected",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row {
                            IconButton(onClick = {
                                val allIds = items.map { it.id }.toSet()
                                selectedIds = if (selectedIds.size == allIds.size) emptySet() else allIds
                            }) {
                                Icon(if (selectedIds.size == items.size) DeXIcons.CheckCircle else DeXIcons.CheckCircleOutlined, null)
                            }
                            IconButton(onClick = { showBulkDeleteConfirm = true }) {
                                Icon(DeXIcons.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var showSortMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(DeXIcons.Sort, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    HistorySort.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(when(order) {
                                                HistorySort.DATE_DESC -> "Newest"
                                                HistorySort.SIZE_DESC -> "Largest"
                                                HistorySort.NAME_ASC -> "A-Z"
                                            }) },
                                            onClick = { TopAppBarState.historySortOrder = order; showSortMenu = false },
                                            leadingIcon = { if (sortOrder == order) Icon(DeXIcons.Check, null, modifier = Modifier.size(18.dp)) }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = {
                                TopAppBarState.historyViewMode = if (viewMode == HistoryViewMode.LIST) HistoryViewMode.GRID else HistoryViewMode.LIST
                            }) {
                                Icon(if (viewMode == HistoryViewMode.LIST) DeXIcons.GridView else DeXIcons.ViewList, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            if (items.isNotEmpty()) {
                                com.dexstudios.dex.ui.components.DeXTextButton(onClick = { showClearConfirm = true }) {
                                    Text(stringResource(R.string.history_clear_all), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HistoryDirection.entries.forEach { dir ->
                            HistoryFilterChip(label = dir.name.lowercase().replaceFirstChar { it.uppercase() }, selected = dirFilter == dir, onClick = { TopAppBarState.historyDirectionFilter = dir })
                        }
                        Box(modifier = Modifier.height(16.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).align(Alignment.CenterVertically))
                        HistoryType.entries.forEach { type ->
                            HistoryFilterChip(label = type.name.lowercase().replaceFirstChar { it.uppercase() }, selected = typeFilter == type, onClick = { TopAppBarState.historyTypeFilter = type })
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                        Icon(DeXIcons.History, null, modifier = Modifier.size(80.dp).graphicsLayer { alpha = 0.2f }, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = if (search.isNotBlank()) "No matching records" else stringResource(R.string.history_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)

                        if (search.isBlank()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            com.dexstudios.dex.ui.components.DeXButton(
                                onClick = { seedDemoHistory(context) }
                            ) {
                                Text("Seed Demo Data")
                            }
                        }
                    }
                }
            } else {
                if (viewMode == HistoryViewMode.LIST) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusBarHeight + 200.dp, bottom = 88.dp - navBarInset),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupOrder.forEach { label ->
                            val groupItems = groupedItems[label] ?: emptyList()
                            if (groupItems.isNotEmpty()) {
                                stickyHeader(key = label) {
                                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 8.dp, horizontal = 8.dp)) {
                                        Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                items(groupItems, key = { it.id }) { record ->
                                    var showItemMenu by remember { mutableStateOf(false) }
                                    val isSelected = selectedIds.contains(record.id)
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                                TransferHistory.delete(context, record.id)
                                                true
                                            } else if (value == SwipeToDismissBoxValue.StartToEnd) {
                                                val uri = record.uri?.toUri()
                                                if (uri != null) {
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                                                        setDataAndType(uri, mime)
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Share file"))
                                                }
                                                false
                                            } else false
                                        }
                                    )

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        backgroundContent = {
                                            val color = when (dismissState.dismissDirection) {
                                                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                                else -> Color.Transparent
                                            }
                                            val alignment = when (dismissState.dismissDirection) {
                                                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                                else -> Alignment.Center
                                            }
                                            val icon = when (dismissState.dismissDirection) {
                                                SwipeToDismissBoxValue.StartToEnd -> DeXIcons.Share
                                                SwipeToDismissBoxValue.EndToStart -> DeXIcons.Delete
                                                else -> null
                                            }
                                            Box(
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(color).padding(horizontal = 24.dp),
                                                contentAlignment = alignment
                                            ) {
                                                icon?.let { Icon(it, null, tint = if (alignment == Alignment.CenterStart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                                            }
                                        },
                                        modifier = Modifier.animateItem()
                                    ) {
                                        HistoryRow(
                                            record = record,
                                            isSelected = isSelected,
                                            isSelectionMode = selectionActive,
                                            onClick = {
                                                if (selectionActive) {
                                                    selectedIds = if (isSelected) selectedIds - record.id else selectedIds + record.id
                                                } else {
                                                    openRecord(context, record, onShowLightbox = { lightboxRecord = record })
                                                }
                                            },
                                            onLongClick = {
                                                if (!selectionActive) selectedIds = setOf(record.id) else showItemMenu = true
                                            },
                                            onThumbnailClick = {
                                                lightboxRecord = record
                                            }
                                        )
                                    }

                                    DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                                        DropdownMenuItem(text = { Text("Delete") }, onClick = { TransferHistory.delete(context, record.id); showItemMenu = false }, leadingIcon = { Icon(DeXIcons.Close, null, modifier = Modifier.size(18.dp)) })
                                        DropdownMenuItem(text = { Text("Share") }, onClick = {
                                            val uri = record.uri?.toUri()
                                            if (uri != null) {
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                                                    setDataAndType(uri, mime); putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share file"))
                                            }
                                            showItemMenu = false
                                        }, leadingIcon = { Icon(DeXIcons.Share, null, modifier = Modifier.size(18.dp)) })
                                        DropdownMenuItem(text = { Text("Open Folder") }, onClick = { record.uri?.toUri()?.let { openFolderOf(context, it) }; showItemMenu = false }, leadingIcon = { Icon(DeXIcons.Folder, null, modifier = Modifier.size(18.dp)) })
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusBarHeight + 200.dp, bottom = 88.dp - navBarInset),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupOrder.forEach { label ->
                            val groupItems = groupedItems[label] ?: emptyList()
                            if (groupItems.isNotEmpty()) {
                                item(key = "grid_header_$label", span = { GridItemSpan(maxLineSpan) }) {
                                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 8.dp, horizontal = 8.dp)) {
                                        Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                items(groupItems, key = { it.id }) { record ->
                                    val isSelected = selectedIds.contains(record.id)
                                    HistoryGridItem(
                                        record = record,
                                        isSelected = isSelected,
                                        isSelectionMode = selectionActive,
                                        onClick = {
                                            if (selectionActive) {
                                                selectedIds = if (isSelected) selectedIds - record.id else selectedIds + record.id
                                            } else {
                                                openRecord(context, record, onShowLightbox = { lightboxRecord = record })
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectionActive) selectedIds = setOf(record.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text(stringResource(R.string.history_clear_confirm_title)) },
                text = { Text(stringResource(R.string.history_clear_confirm_desc)) },
                confirmButton = {
                    TextButton(onClick = { TransferHistory.clear(context); showClearConfirm = false }) {
                        Text(stringResource(R.string.history_clear_all), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        if (showBulkDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBulkDeleteConfirm = false },
                title = { Text("Delete ${selectedIds.size} records?") },
                text = { Text("This will permanently remove these records from your history.") },
                confirmButton = {
                    TextButton(onClick = {
                        selectedIds.forEach { id -> TransferHistory.delete(context, id) }
                        selectedIds = emptySet()
                        showBulkDeleteConfirm = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        lightboxRecord?.let {
            HistoryLightbox(
                record = it,
                onDismiss = { lightboxRecord = null },
                backdrop = contentBackdrop
            )
        }
    }
}

@Composable
private fun HistoryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.clip(CircleShape).bubbleFluidity(targetScale = 0.95f),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryRow(
    record: TransferRecord,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onThumbnailClick: () -> Unit
) {
    val isSent = record.direction == "sent"
    val fileIcon = remember(record.name) { getFileIcon(record.name) }
    val type = remember(record.name) { getHistoryType(record.name) }
    val hasThumbnail = (type == HistoryType.IMAGES || type == HistoryType.VIDEOS) && record.uri != null
    val isFailed = record.status != "success"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .graphicsLayer { alpha = if (isFailed && !isSelected) 0.6f else 1f }
            .bubbleFluidity(targetScale = 0.98f, pullFactor = 0.1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) DeXIcons.CheckCircle else DeXIcons.CheckCircleOutlined,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 12.dp).size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else if (isSent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                .clickable(enabled = hasThumbnail, onClick = onThumbnailClick),
            contentAlignment = Alignment.Center
        ) {
            if (hasThumbnail) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(record.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp).align(Alignment.Center), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        }
                    },
                    error = {
                        Icon(imageVector = fileIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    }
                )
                if (isFailed) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                }
            } else {
                Icon(imageVector = fileIcon, contentDescription = null, tint = if (isFailed) MaterialTheme.colorScheme.error else if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
            Icon(
                imageVector = if (isSent) ImageVector.vectorResource(R.drawable.ic_send) else DeXIcons.ExpandMore,
                contentDescription = null,
                tint = (if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary).copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(10.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = record.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            val subText = buildString {
                append(formatSize(record.size)); append(" · "); append(formatDate(record.timestamp))
                record.peerDevice?.let { append(" · "); append(it) }
            }
            Text(text = subText, style = MaterialTheme.typography.bodySmall, color = if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isFailed) {
            Text(text = record.status.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun HistoryGridItem(
    record: TransferRecord,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isSent = record.direction == "sent"
    val fileIcon = remember(record.name) { getFileIcon(record.name) }
    val type = remember(record.name) { getHistoryType(record.name) }
    val hasThumbnail = (type == HistoryType.IMAGES || type == HistoryType.VIDEOS) && record.uri != null
    val isFailed = record.status != "success"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .graphicsLayer { alpha = if (isFailed && !isSelected) 0.6f else 1f }
            .bubbleFluidity(targetScale = 0.95f, pullFactor = 0.1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else if (isSent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            if (hasThumbnail) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(record.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                    },
                    error = {
                        Icon(imageVector = fileIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                    }
                )
            } else {
                Icon(imageVector = fileIcon, contentDescription = null, tint = if (isFailed) MaterialTheme.colorScheme.error else if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
            }

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(DeXIcons.Check, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Icon(
                imageVector = if (isSent) ImageVector.vectorResource(R.drawable.ic_send) else DeXIcons.ExpandMore,
                contentDescription = null,
                tint = (if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary).copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = record.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Text(
            text = formatSize(record.size),
            style = MaterialTheme.typography.bodySmall,
            color = if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun seedDemoHistory(context: Context) {
    val now = System.currentTimeMillis()
    val day = 24 * 60 * 60 * 1000L
    val pkg = context.packageName
    fun resUri(resId: Int) = "android.resource://$pkg/$resId"

    // USER PROVIDED FILES
    TransferHistory.log(context, "Screenshot 2026-07-24 181156.png", 1250000L, "received", uri = resUri(R.drawable.wallpaper_gaming), peerDevice = "Nico's PC", timestamp = now)
    TransferHistory.log(context, "IMG-20260521-WA4440001.jpg", 3450000L, "sent", uri = resUri(R.drawable.wallpaper_laptop), peerDevice = "Pixel 8 Pro", timestamp = now - 1000 * 60 * 5)
    TransferHistory.log(context, "MoveCertificate-v1.5.7.zip", 870000L, "received", peerDevice = "Nico's PC", timestamp = now - 1000 * 60 * 15)
    TransferHistory.log(context, "My Passport Doc.pdf", 2100000L, "sent", peerDevice = "Work Laptop", timestamp = now - 1000 * 60 * 45)

    // OTHER DEMO DATA
    TransferHistory.log(context, "vacation_photo.jpg", 2500000L, "received", uri = resUri(R.drawable.wallpaper_server), peerDevice = "Nico's iPhone", timestamp = now - day)
    TransferHistory.log(context, "Dex_v1.2_alpha.apk", 85000000L, "sent", peerDevice = "Testing Tablet", timestamp = now - day - 1000 * 60 * 120)
    TransferHistory.log(context, "archive_2025.zip", 520000000L, "sent", peerDevice = "Home Server", timestamp = now - 15 * day)
}

private fun formatSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return dateFormat.format(Date(timestamp))
}

private fun openFolderOf(context: Context, fileUri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            // For SAF URIs, try to open the directory.
            // Note: This works best with file manager apps that support the directory MIME type.
            setDataAndType(fileUri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open Folder"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open folders", Toast.LENGTH_SHORT).show()
    }
}

private fun openRecord(context: Context, record: TransferRecord, onShowLightbox: () -> Unit) {
    val uriStr = record.uri
    if (uriStr == null) {
        Toast.makeText(context, context.getString(R.string.history_no_source), Toast.LENGTH_SHORT).show()
        return
    }

    val uri = uriStr.toUri()

    // Handle Demo/Dummy files (android.resource://)
    if (uri.scheme == "android.resource") {
        val type = getHistoryType(record.name)
        if (type == HistoryType.IMAGES || type == HistoryType.VIDEOS) {
            onShowLightbox()
        } else {
            Toast.makeText(context, "This is a demo file and cannot be opened externally.", Toast.LENGTH_SHORT).show()
        }
        return
    }

    try {
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: context.contentResolver.getType(uri) // Retry if first failed
            ?: "application/octet-stream"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.history_open_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun getHistoryType(fileName: String): HistoryType {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> HistoryType.IMAGES
        "mp4", "mkv", "mov", "avi", "3gp", "webm" -> HistoryType.VIDEOS
        "pdf", "doc", "docx", "txt", "pptx", "xlsx", "epub", "csv" -> HistoryType.DOCUMENTS
        "apk", "aab" -> HistoryType.APPS
        else -> HistoryType.ALL
    }
}

private fun getFileIcon(fileName: String): ImageVector {
    val type = getHistoryType(fileName)
    return when (type) {
        HistoryType.IMAGES -> DeXIcons.Photo
        HistoryType.VIDEOS -> DeXIcons.VideoCamera
        HistoryType.DOCUMENTS -> DeXIcons.Article
        HistoryType.APPS -> DeXIcons.Inventory
        else -> DeXIcons.Article
    }
}

private fun getDateGroupLabel(timestamp: Long): String {
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { timeInMillis = timestamp }
    if (now.get(Calendar.YEAR) == time.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR)) return "Today"
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(Calendar.YEAR) == time.get(Calendar.YEAR) && yesterday.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR)) return "Yesterday"
    val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
    if (time.after(weekAgo)) return "This Week"
    return "Older"
}

@Preview(showBackground = true)
@Composable
fun HistoryGridPreview() {
    MaterialTheme {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(5) { i ->
                HistoryGridItem(
                    record = TransferRecord(
                        id = "$i",
                        name = "File $i.jpg",
                        size = 1250000L,
                        timestamp = System.currentTimeMillis(),
                        direction = if (i % 2 == 0) "received" else "sent",
                        peerDevice = "Device $i"
                    ),
                    onClick = {},
                    onLongClick = {}
                )
            }
        }
    }
}
