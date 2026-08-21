package com.dexstudios.dex.feature.history

import androidx.compose.animation.*
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
import org.jetbrains.compose.resources.painterResource
import com.dexstudios.dex.core.designsystem.generated.resources.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dexstudios.dex.core.designsystem.generated.resources.*
import com.dexstudios.dex.core.designsystem.state.HistoryType
import com.dexstudios.dex.core.designsystem.state.HistorySort
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.dexstudios.dex.core.designsystem.state.HistoryViewMode
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.state.TopAppBarState
import com.dexstudios.dex.core.designsystem.state.HistoryDirection
import coil3.compose.LocalPlatformContext
import com.dexstudios.dex.core.network.TransferHistory
import com.dexstudios.dex.core.network.TransferRecord
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.dexstudios.dex.core.designsystem.components.DeXScrollbar
import java.util.Date

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val clientEngine: com.dexstudios.dex.core.network.ClientEngine = org.koin.compose.koinInject()
    val platformHelper = rememberHistoryPlatformHelper()
    val allItems by TransferHistory.items.collectAsStateWithLifecycle()
    val search = TopAppBarState.searchQuery
    val dirFilter = TopAppBarState.historyDirectionFilter
    val typeFilter = TopAppBarState.historyTypeFilter
    val sortOrder = TopAppBarState.historySortOrder
    val viewMode = TopAppBarState.historyViewMode
    val isFilterVisible = TopAppBarState.isHistoryFilterVisible
    val isSearchExpanded = TopAppBarState.isSearchExpanded

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
        TransferHistory.refresh()
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
                                Icon(painterResource(Res.drawable.ic_fluent_close), contentDescription = "Cancel")
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
                                Icon(if (selectedIds.size == items.size) painterResource(Res.drawable.ic_fluent_check_circle) else painterResource(Res.drawable.ic_fluent_check_circle_outlined), null)
                            }
                            IconButton(onClick = { showBulkDeleteConfirm = true }) {
                                Icon(painterResource(Res.drawable.ic_fluent_delete), null, tint = MaterialTheme.colorScheme.error)
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
                            text = stringResource(Res.string.history_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (!isSearchExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { TopAppBarState.isHistoryFilterVisible = !isFilterVisible }) {
                                    Icon(painterResource(Res.drawable.ic_fluent_filter_list), null, tint = if (isFilterVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = {
                                    TopAppBarState.historyViewMode = if (viewMode == HistoryViewMode.LIST) HistoryViewMode.GRID else HistoryViewMode.LIST
                                }) {
                                    Icon(if (viewMode == HistoryViewMode.LIST) painterResource(Res.drawable.ic_fluent_grid_view) else painterResource(Res.drawable.ic_fluent_view_list), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                                var showMoreMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(painterResource(Res.drawable.ic_fluent_more_vert), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    }
                                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                        // Sort Submenu
                                        var showSortSubmenu by remember { mutableStateOf(false) }
                                        DropdownMenuItem(
                                            text = { Text("Sort by...") },
                                            onClick = { showSortSubmenu = true },
                                            leadingIcon = { Icon(painterResource(Res.drawable.ic_fluent_sort), null, modifier = Modifier.size(18.dp)) },
                                            trailingIcon = { Icon(painterResource(Res.drawable.ic_fluent_expand_more), null, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = -90f }) }
                                        )
                                        if (items.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(Res.string.history_clear_all)) },
                                                onClick = { showClearConfirm = true; showMoreMenu = false },
                                                leadingIcon = { Icon(painterResource(Res.drawable.ic_fluent_delete), null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                                            )
                                        }

                                        DropdownMenu(expanded = showSortSubmenu, onDismissRequest = { showSortSubmenu = false }) {
                                            HistorySort.entries.forEach { order ->
                                                DropdownMenuItem(
                                                    text = { Text(when(order) {
                                                        HistorySort.DATE_DESC -> "Newest"
                                                        HistorySort.SIZE_DESC -> "Largest"
                                                        HistorySort.NAME_ASC -> "A-Z"
                                                    }) },
                                                    onClick = { TopAppBarState.historySortOrder = order; showSortSubmenu = false; showMoreMenu = false },
                                                    leadingIcon = { if (sortOrder == order) Icon(painterResource(Res.drawable.ic_fluent_check), null, modifier = Modifier.size(18.dp)) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isFilterVisible && !isSearchExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
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
            }

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                        Icon(painterResource(Res.drawable.ic_fluent_history), null, modifier = Modifier.size(80.dp).graphicsLayer { alpha = 0.2f }, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = if (search.isNotBlank()) "No matching records" else stringResource(Res.string.history_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)

                        if (search.isBlank()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.dexstudios.dex.core.designsystem.components.DeXButton(
                                    onClick = { seedDemoHistory() }
                                ) {
                                    Text("Seed Demo Data")
                                }
                                com.dexstudios.dex.core.designsystem.components.DeXButton(
                                    onClick = {}
                                ) {
                                    Text("Demo Down")
                                }
                                com.dexstudios.dex.core.designsystem.components.DeXButton(
                                    onClick = { clientEngine.triggerDemo() }
                                ) {
                                    Text("Demo Up")
                                }
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
                                    HistoryRow(
                                        record = record,
                                        isSelected = isSelected,
                                        isSelectionMode = selectionActive,
                                        onClick = {
                                            if (selectionActive) {
                                                selectedIds = if (isSelected) selectedIds - record.id else selectedIds + record.id
                                            } else {
                                                openRecord(platformHelper, record, onShowLightbox = { lightboxRecord = record })
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectionActive) selectedIds = setOf(record.id) else showItemMenu = true
                                        },
                                        onThumbnailClick = {
                                            lightboxRecord = record
                                        },
                                        modifier = Modifier.animateItem()
                                    )

                                    DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                                        DropdownMenuItem(text = { Text("Delete") }, onClick = { TransferHistory.delete(record.id); showItemMenu = false }, leadingIcon = { Icon(painterResource(Res.drawable.ic_fluent_close), null, modifier = Modifier.size(18.dp)) })
                                        DropdownMenuItem(text = { Text("Share") }, onClick = {
                                            record.uri?.let { uri -> platformHelper.shareFile(uri) }
                                            showItemMenu = false
                                        }, leadingIcon = { Icon(painterResource(Res.drawable.ic_fluent_ios_share), null, modifier = Modifier.size(18.dp)) })
                                        DropdownMenuItem(text = { Text("Open Folder") }, onClick = { record.uri?.let { platformHelper.openFolder(it) }; showItemMenu = false }, leadingIcon = { Icon(painterResource(Res.drawable.ic_fluent_folder), null, modifier = Modifier.size(18.dp)) })
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
                                                openRecord(platformHelper, record, onShowLightbox = { lightboxRecord = record })
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
                title = { Text(stringResource(Res.string.history_clear_confirm_title)) },
                text = { Text(stringResource(Res.string.history_clear_confirm_desc)) },
                confirmButton = {
                    TextButton(onClick = { TransferHistory.clear(); showClearConfirm = false }) {
                        Text(stringResource(Res.string.history_clear_all), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(Res.string.cancel)) }
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
                        selectedIds.forEach { id -> TransferHistory.delete(id) }
                        selectedIds = emptySet()
                        showBulkDeleteConfirm = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirm = false }) { Text(stringResource(Res.string.cancel)) }
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
    modifier: Modifier = Modifier,
    record: TransferRecord,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onThumbnailClick: () -> Unit
) {
    val isSent = record.direction == "sent"
    val fileIcon = getFileIcon(record.name)
    val type = remember(record.name) { getHistoryType(record.name) }
    val hasThumbnail = (type == HistoryType.IMAGES || type == HistoryType.VIDEOS) && record.uri != null
    val isFailed = record.status != "success"

    Row(
        modifier = modifier
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
                painter = if (isSelected) painterResource(Res.drawable.ic_fluent_check_circle) else painterResource(Res.drawable.ic_fluent_check_circle_outlined),
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
                    model = ImageRequest.Builder(LocalPlatformContext.current)
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
                        Icon(painter = fileIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    }
                )
                if (isFailed) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                }
            } else {
                Icon(painter = fileIcon, contentDescription = null, tint = if (isFailed) MaterialTheme.colorScheme.error else if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
            Icon(
                painter = if (isSent) painterResource(Res.drawable.ic_fluent_file_upload) else painterResource(Res.drawable.ic_fluent_file_download),
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
                append(formatSize(record.size))
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
    val fileIcon = getFileIcon(record.name)
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
                    model = ImageRequest.Builder(LocalPlatformContext.current)
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
                        Icon(painter = fileIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                    }
                )
            } else {
                Icon(painter = fileIcon, contentDescription = null, tint = if (isFailed) MaterialTheme.colorScheme.error else if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
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
                        Icon(painterResource(Res.drawable.ic_fluent_check), null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Icon(
                painter = if (isSent) painterResource(Res.drawable.ic_fluent_file_upload) else painterResource(Res.drawable.ic_fluent_file_download),
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

private fun seedDemoHistory() {
    val now = System.currentTimeMillis()
    val day = 24 * 60 * 60 * 1000L
    fun resUri(resName: String) = "android.resource://com.dexstudios.dex/drawable/$resName"

    // USER PROVIDED FILES
    TransferHistory.log("Screenshot 2026-07-24 181156.png", 1250000L, "received", uri = resUri("wallpaper_gaming"), peerDevice = "Nico's PC", timestamp = now)
    TransferHistory.log("IMG-20260521-WA4440001.jpg", 3450000L, "sent", uri = resUri("wallpaper_laptop"), peerDevice = "Pixel 8 Pro", timestamp = now - 1000 * 60 * 5)
    TransferHistory.log("MoveCertificate-v1.5.7.zip", 870000L, "received", peerDevice = "Nico's PC", timestamp = now - 1000 * 60 * 15)
    TransferHistory.log("My Passport Doc.pdf", 2100000L, "sent", peerDevice = "Work Laptop", timestamp = now - 1000 * 60 * 45)

    // OTHER DEMO DATA
    TransferHistory.log("vacation_photo.jpg", 2500000L, "received", uri = resUri("wallpaper_server"), peerDevice = "Nico's iPhone", timestamp = now - day)
    TransferHistory.log("Dex_v1.2_alpha.apk", 85000000L, "sent", peerDevice = "Testing Tablet", timestamp = now - day - 1000 * 60 * 120)
    TransferHistory.log("archive_2025.zip", 520000000L, "sent", peerDevice = "Home Server", timestamp = now - 15 * day)
}

private fun formatSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> ((bytes / gb * 10.0).toLong() / 10.0).toString() + " GB"
        bytes >= mb -> ((bytes / mb * 10.0).toLong() / 10.0).toString() + " MB"
        bytes >= kb -> ((bytes / kb * 10.0).toLong() / 10.0).toString() + " KB"
        else -> "$bytes B"
    }
}


private fun openFolderOf(platformHelper: HistoryPlatformHelper, fileUri: String) {
    platformHelper.openFolder(fileUri)
}

private fun openRecord(platformHelper: HistoryPlatformHelper, record: TransferRecord, onShowLightbox: () -> Unit) {
    val uriStr = record.uri
    if (uriStr == null) {
        platformHelper.showToast("No source available")
        return
    }

    if (uriStr.startsWith("android.resource")) {
        val type = getHistoryType(record.name)
        if (type == HistoryType.IMAGES || type == HistoryType.VIDEOS) {
            onShowLightbox()
        } else {
            platformHelper.showToast("This is a demo file and cannot be opened externally.")
        }
        return
    }

    platformHelper.openFile(uriStr, null)
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

@Composable
private fun getFileIcon(fileName: String): androidx.compose.ui.graphics.painter.Painter {
    val type = getHistoryType(fileName)
    return when (type) {
        HistoryType.IMAGES -> painterResource(Res.drawable.ic_fluent_photo)
        HistoryType.VIDEOS -> painterResource(Res.drawable.ic_fluent_video_camera)
        HistoryType.DOCUMENTS -> painterResource(Res.drawable.ic_fluent_article)
        HistoryType.APPS -> painterResource(Res.drawable.ic_fluent_inventory)
        else -> painterResource(Res.drawable.ic_fluent_article)
    }
}

private fun getDateGroupLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val day = 24 * 60 * 60 * 1000L
    val diff = now - timestamp
    return when {
        diff < day -> "Today"
        diff < 2 * day -> "Yesterday"
        diff < 7 * day -> "This Week"
        else -> "Older"
    }
}

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







