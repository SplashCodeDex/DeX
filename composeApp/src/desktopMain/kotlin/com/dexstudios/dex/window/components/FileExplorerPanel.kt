package com.dexstudios.dex.window.components
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_arrow_back
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_search
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_history
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_inventory
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_article
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_photo
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_file_upload
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_close

import com.dexstudios.dex.core.designsystem.generated.resources.Res

import org.jetbrains.compose.resources.painterResource

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.TransferHistory
import com.dexstudios.dex.core.network.TransferRecord
import com.dexstudios.dex.core.network.UploadState
import com.dexstudios.dex.core.network.services.ExplorerFileEntry
import com.dexstudios.dex.core.network.services.ExplorerFolderItem
import com.dexstudios.dex.core.network.services.FileExplorerService
import com.dexstudios.dex.core.network.services.PullFileItem
import com.dexstudios.dex.mirror.toImageBitmap
import com.dexstudios.dex.window.DockedWindowStateController
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Base64
import javax.swing.JFileChooser

enum class ExplorerMode {
    History,
    Saf
}

data class ExplorerFileItem(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val timestamp: Long,
    val uri: String? = null,
    val thumbBase64: String? = null,
    val isAddFolderButton: Boolean = false
)

/**
 * FileExplorerPanel:
 * - Row 0: Top Navigation (36dp UpDir button, 40dp debounced search pill, SAF vs History mode toggle)
 * - Row 1: LazyVerticalGrid of 100x105dp cards (48x48dp thumbnails, hover lift, press sink, 400ms double-click guard)
 * - Row 2: Action Dock ("Send Files", "Send Folders", floating PullProgressDock toast with 4dp emerald progress bar)
 */
@Composable
fun FileExplorerPanel(
    controller: DockedWindowStateController? = null,
    onSendFiles: () -> Unit = {},
    onSendFolders: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
    clientEngine: ClientEngine = koinInject(),
    fileExplorerService: FileExplorerService = koinInject(),
    discoveryEngine: DiscoveryEngine = koinInject()
) {
    val coroutineScope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(ExplorerMode.History) }
    var currentLocalPath by remember { mutableStateOf(getDeXDownloadDirectory()) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var lastClickTime by remember { mutableStateOf(0L) }
    var lastClickedItemId by remember { mutableStateOf<String?>(null) }

    // SAF State
    var safFolders by remember { mutableStateOf<List<ExplorerFolderItem>>(emptyList()) }
    var safBreadcrumb by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // Pair(name, uri)
    var safEntries by remember { mutableStateOf<List<ExplorerFileEntry>>(emptyList()) }
    var isLoadingSaf by remember { mutableStateOf(false) }

    // Paired Phone Detection
    val pairedFingerprints by AuthState.pairedFingerprints.collectAsState()
    val devicesMap by discoveryEngine.devices.collectAsState()
    val activePhone = remember(devicesMap, pairedFingerprints) {
        devicesMap.values.firstOrNull { pairedFingerprints.contains(it.info.fingerprint) }
            ?: devicesMap.values.firstOrNull()
    }
    val activeFingerprint = activePhone?.info?.fingerprint ?: pairedFingerprints.firstOrNull() ?: ""

    // Real-time transfer records from TransferHistory & Pull progress
    val transferHistoryItems by TransferHistory.items.collectAsState()
    
    // Defer rapid progress updates to prevent massive top-level recompositions
    val isTransferring by remember(clientEngine, fileExplorerService) {
        combine(clientEngine.uploadState, fileExplorerService.pullProgress) { upload, pull ->
            upload.isUploading || pull.isPulling
        }.distinctUntilChanged()
    }.collectAsState(initial = false)

    // 150ms search debounce
    LaunchedEffect(searchQuery) {
        delay(150)
        debouncedQuery = searchQuery
    }

    // Refresh history on display
    LaunchedEffect(Unit) {
        TransferHistory.init()
    }

    // Load SAF root folders when switching to SAF mode
    LaunchedEffect(mode, activeFingerprint) {
        if (mode == ExplorerMode.Saf && activeFingerprint.isNotBlank()) {
            isLoadingSaf = true
            safBreadcrumb = emptyList()
            safFolders = fileExplorerService.listFolders(activeFingerprint)
            isLoadingSaf = false
        }
    }

    // Load SAF directory entries when breadcrumb changes
    LaunchedEffect(safBreadcrumb, activeFingerprint) {
        if (mode == ExplorerMode.Saf && safBreadcrumb.isNotEmpty() && activeFingerprint.isNotBlank()) {
            isLoadingSaf = true
            val currentFolderUri = safBreadcrumb.last().second
            safEntries = fileExplorerService.browseFolder(activeFingerprint, currentFolderUri)
            isLoadingSaf = false
        }
    }

    // Determine current files to display
    val displayedFiles: List<ExplorerFileItem> = remember(
        mode, currentLocalPath, transferHistoryItems, safFolders, safBreadcrumb, safEntries, debouncedQuery
    ) {
        val rawItems = if (mode == ExplorerMode.History) {
            // Local download folder items + transfer history
            val folder = File(currentLocalPath)
            val diskFiles = if (folder.exists() && folder.isDirectory) {
                folder.listFiles()?.map { f ->
                    ExplorerFileItem(
                        id = f.absolutePath,
                        name = f.name,
                        path = f.absolutePath,
                        size = if (f.isDirectory) 0L else f.length(),
                        isDirectory = f.isDirectory,
                        timestamp = f.lastModified()
                    )
                } ?: emptyList()
            } else emptyList()

            if (diskFiles.isEmpty() && transferHistoryItems.isNotEmpty()) {
                transferHistoryItems.map { record ->
                    ExplorerFileItem(
                        id = record.id,
                        name = record.name,
                        path = record.uri ?: "",
                        size = record.size,
                        isDirectory = false,
                        timestamp = record.timestamp,
                        uri = record.uri
                    )
                }
            } else {
                diskFiles
            }
        } else {
            // SAF Phone Mode
            if (safBreadcrumb.isEmpty()) {
                val folderItems = safFolders.map { f ->
                    ExplorerFileItem(
                        id = f.id,
                        name = f.name,
                        path = f.uri,
                        size = 0L,
                        isDirectory = true,
                        timestamp = System.currentTimeMillis(),
                        uri = f.uri
                    )
                }
                folderItems + ExplorerFileItem(
                    id = "add_saf_folder",
                    name = "+ Add Folder",
                    path = "",
                    size = 0L,
                    isDirectory = true,
                    timestamp = 0L,
                    isAddFolderButton = true
                )
            } else {
                safEntries.map { e ->
                    ExplorerFileItem(
                        id = e.uri,
                        name = e.name,
                        path = e.uri,
                        size = e.size,
                        isDirectory = e.isDirectory,
                        timestamp = System.currentTimeMillis(),
                        uri = e.uri,
                        thumbBase64 = e.thumbBase64
                    )
                }
            }
        }

        if (debouncedQuery.isBlank()) {
            rawItems
        } else {
            rawItems.filter { it.name.contains(debouncedQuery, ignoreCase = true) }
        }
    }

    val isAtRoot = if (mode == ExplorerMode.History) {
        currentLocalPath == getDeXDownloadDirectory() || File(currentLocalPath).parent == null
    } else {
        safBreadcrumb.isEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 20.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // === Row 0: Top Navigation Controls ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 36dp Circular Up-Dir Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .alpha(if (!isAtRoot) 1.0f else 0.4f)
                    .clickable(enabled = !isAtRoot) {
                        if (mode == ExplorerMode.History) {
                            val parent = File(currentLocalPath).parent
                            if (parent != null) {
                                currentLocalPath = parent
                            }
                        } else {
                            if (safBreadcrumb.isNotEmpty()) {
                                safBreadcrumb = safBreadcrumb.dropLast(1)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_fluent_arrow_back),
                    contentDescription = "Up Directory",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Current Path / Folder Indicator or Search Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_search),
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp).size(14.dp)
                    )

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                val hint = if (mode == ExplorerMode.History) {
                                    "Search downloads (${File(currentLocalPath).name})..."
                                } else {
                                    val currentSafName = safBreadcrumb.lastOrNull()?.first ?: (activePhone?.info?.alias ?: "Phone")
                                    "Search $currentSafName..."
                                }
                                Text(
                                    text = hint,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 36dp Mode Toggle Button (SAF Phone vs PC History)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        mode = if (mode == ExplorerMode.History) ExplorerMode.Saf else ExplorerMode.History
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = if (mode == ExplorerMode.History) painterResource(Res.drawable.ic_fluent_history) else painterResource(Res.drawable.ic_fluent_smartphone),
                    contentDescription = "Toggle Explorer Mode",
                    tint = if (mode == ExplorerMode.Saf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // === Row 1: Middle Grid Area ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoadingSaf) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Loading phone storage...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (displayedFiles.isEmpty()) {
                // Empty State Overlay
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_folder),
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (debouncedQuery.isNotBlank()) "No matching files" else "No transfers yet",
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = if (mode == ExplorerMode.History) "Received files appear here" else "Shared folders from phone appear here",
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedFiles, key = { it.id }) { item ->
                        FileGridItemCard(
                            item = item,
                            isSelected = selectedItemId == item.id,
                            onClick = {
                                if (item.isAddFolderButton) {
                                    coroutineScope.launch {
                                        isLoadingSaf = true
                                        val newFolder = fileExplorerService.grantFolder(activeFingerprint)
                                        if (newFolder != null) {
                                            safFolders = fileExplorerService.listFolders(activeFingerprint)
                                        }
                                        isLoadingSaf = false
                                    }
                                    return@FileGridItemCard
                                }

                                val now = System.currentTimeMillis()
                                if (lastClickedItemId == item.id && now - lastClickTime < 400L) {
                                    // Double-click Action with 400ms delta filter guard
                                    if (mode == ExplorerMode.History) {
                                        handleItemDoubleClick(item, onDrillDown = { currentLocalPath = it })
                                    } else {
                                        // SAF Mode: Drill down into folder or Pull file
                                        if (item.isDirectory) {
                                            safBreadcrumb = safBreadcrumb + Pair(item.name, item.uri ?: item.path)
                                        } else if (item.uri != null && activeFingerprint.isNotBlank()) {
                                            coroutineScope.launch {
                                                fileExplorerService.pullFiles(
                                                    activeFingerprint,
                                                    listOf(PullFileItem(uri = item.uri, name = item.name, size = item.size))
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    selectedItemId = item.id
                                    lastClickedItemId = item.id
                                    lastClickTime = now
                                }
                            }
                        )
                    }
                }
            }

            // Floating PullProgressDock Toast
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                AnimatedVisibility(
                    visible = isTransferring,
                    enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(300)) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { 50 }, animationSpec = tween(300)) + fadeOut()
                ) {
                    PullProgressDock(
                        clientEngine = clientEngine,
                        onCancel = {
                            clientEngine.resetUploadState()
                            val currentPull = fileExplorerService.pullProgress.value
                            if (currentPull.isPulling && activeFingerprint.isNotBlank()) {
                                coroutineScope.launch {
                                    fileExplorerService.cancelPull(activeFingerprint, currentPull.requestId)
                                }
                            }
                        }
                    )
                }
            }
        }

        // === Row 2: Bottom Actions Dock ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 1.dp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Send Files Action (Native File Picker)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            controller?.isModalDialogOpen = true
                            onSendFiles()
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val dialog = FileDialog(null as Frame?, "Select Files to Send", FileDialog.LOAD)
                                    dialog.isMultipleMode = true
                                    dialog.isVisible = true
                                    val files = dialog.files
                                    if (files != null && files.isNotEmpty()) {
                                        println("Selected ${files.size} files to send")
                                    }
                                } finally {
                                    controller?.isModalDialogOpen = false
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_file_upload),
                        contentDescription = "Send Files",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp)
                    )
                    Text(
                        text = "Send Files",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Send Folders Action (Native Directory Picker)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            controller?.isModalDialogOpen = true
                            onSendFolders()
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val chooser = JFileChooser()
                                    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                    chooser.dialogTitle = "Select Folder to Send"
                                    val res = chooser.showOpenDialog(null)
                                    if (res == JFileChooser.APPROVE_OPTION) {
                                        val folder = chooser.selectedFile
                                        println("Selected folder to send: ${folder.absolutePath}")
                                    }
                                } finally {
                                    controller?.isModalDialogOpen = false
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_folder),
                        contentDescription = "Send Folders",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp)
                    )
                    Text(
                        text = "Send Folders",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 100x105dp File & Folder Grid Card with hover lift, press sink, and micro-thumbnail decoding.
 */
@Composable
private fun FileGridItemCard(
    item: ExplorerFileItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1.0f,
        animationSpec = tween(300, easing = com.dexstudios.dex.window.kinematics.DockCardPhysics.HoverEase),
        label = "itemScale"
    )

    // Decode Base64 micro-thumbnail if present
    val thumbnailBitmap = remember(item.thumbBase64) {
        if (!item.thumbBase64.isNullOrBlank()) {
            try {
                val bytes = Base64.getDecoder().decode(item.thumbBase64)
                bytes.toImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 105.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .bubbleFluidity()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected && isHovered -> androidx.compose.ui.graphics.Color(0xFF3D3647)
                    isSelected -> androidx.compose.ui.graphics.Color(0xFF332D3B)
                    isHovered -> MaterialTheme.colorScheme.surfaceVariant
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // 48x48dp Icon or Micro-Thumbnail Glyph
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = getFileIcon(item),
                        contentDescription = item.name,
                        tint = getFileIconColor(item),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // File Name Label
            Text(
                text = item.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            // File Size or Timestamp Sub-label
            Text(
                text = if (item.isDirectory) (if (item.isAddFolderButton) "SAF Picker" else "Folder") else formatFileSize(item.size),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Floating PullProgressDock Toast with a 4dp Emerald Progress Bar.
 */
@Composable
fun PullProgressDock(
    clientEngine: ClientEngine,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uploadState by clientEngine.uploadState.collectAsState()
    
    Box(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141118))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uploadState.fileName.ifBlank { "Transferring files..." },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${(uploadState.progress * 100).toInt()}% • ${formatSpeed(uploadState.speedBps)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    painter = painterResource(Res.drawable.ic_fluent_close),
                    contentDescription = "Cancel Transfer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onCancel() }
                        .padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4dp Emerald Progress Indicator
            LinearProgressIndicator(
                progress = { uploadState.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun getFileIcon(item: ExplorerFileItem): androidx.compose.ui.graphics.painter.Painter {
    if (item.isAddFolderButton) return painterResource(Res.drawable.ic_fluent_folder)
    if (item.isDirectory) return painterResource(Res.drawable.ic_fluent_folder)
    val ext = item.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> painterResource(Res.drawable.ic_fluent_photo)
        "zip", "rar", "7z", "tar", "gz" -> painterResource(Res.drawable.ic_fluent_inventory)
        else -> painterResource(Res.drawable.ic_fluent_article)
    }
}

private fun getFileIconColor(item: ExplorerFileItem): Color {
    if (item.isAddFolderButton) return Color(0xFF10B981) // Emerald
    if (item.isDirectory) return Color(0xFFFBBF24) // Amber Folder
    val ext = item.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> Color(0xFF60A5FA) // Blue
        "mp4", "mkv", "avi", "mov" -> Color(0xFFF472B6) // Pink
        "mp3", "wav", "flac", "m4a" -> Color(0xFFA78BFA) // Purple
        "pdf", "doc", "docx", "txt" -> Color(0xFF34D399) // Emerald
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFF9CA3AF) // Gray
    }
}

private fun handleItemDoubleClick(item: ExplorerFileItem, onDrillDown: (String) -> Unit) {
    if (item.isDirectory) {
        onDrillDown(item.path)
    } else {
        // Dangerous file launch protection
        val ext = item.name.substringAfterLast('.', "").lowercase()
        val dangerousExtensions = setOf("exe", "bat", "cmd", "msi", "ps1", "vbs", "jar")
        if (ext in dangerousExtensions) {
            println("Security block: dangerous executable file prevented from direct double-click launch: ${item.name}")
            return
        }

        try {
            val file = File(item.path)
            if (file.exists() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format("%.1f %s", value, units[digitGroups])
}

private fun formatSpeed(bps: Long): String {
    val mbps = bps / (1024.0 * 1024.0)
    return String.format("%.1f MB/s", mbps)
}

fun getDeXDownloadDirectory(): String {
    val userHome = System.getProperty("user.home") ?: ""
    val dir = File(userHome, "Downloads/DeX")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir.absolutePath
}
