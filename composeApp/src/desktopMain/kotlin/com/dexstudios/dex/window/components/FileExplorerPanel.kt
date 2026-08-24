package com.dexstudios.dex.window.components
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_arrow_back
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_file_upload
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_history
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_search
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.core.designsystem.icons.AnimatedSearchToXIcon
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.services.FileExplorerService
import com.dexstudios.dex.window.DockedWindowStateController
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JFileChooser

/**
 * FileExplorerPanel:
 * - Row 0: Top Navigation (36dp UpDir button, 40dp debounced search pill, SAF vs History mode toggle)
 * - Row 1: LazyVerticalGrid of 100x105dp cards (48x48dp thumbnails, hover lift, press sink, 400ms double-click guard)
 * - Row 2: Action Dock ("Send Files", "Send Folders", floating PullProgressDock toast with 4dp emerald progress bar)
 *
 * State and business logic live in [FileExplorerViewModel]; this container only renders.
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
    discoveryEngine: DiscoveryEngine = koinInject(),
) {
    val viewModel: FileExplorerViewModel = viewModel {
        FileExplorerViewModel(clientEngine, fileExplorerService, discoveryEngine)
    }

    val coroutineScope = rememberCoroutineScope()
    var lastClickTime by remember { mutableStateOf(0L) }
    var lastClickedItemId by remember { mutableStateOf<String?>(null) }

    val mode by viewModel.mode.collectAsState()
    val currentLocalPath by viewModel.currentLocalPath.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val debouncedQuery by viewModel.debouncedQuery.collectAsState()
    val selectedItemId by viewModel.selectedItemId.collectAsState()
    val displayedFiles by viewModel.displayedFiles.collectAsState()
    val isAtRoot by viewModel.isAtRoot.collectAsState()
    val isLoadingSaf by viewModel.isLoadingSaf.collectAsState()
    val safBreadcrumb by viewModel.safBreadcrumb.collectAsState()
    val activePhone by viewModel.activePhone.collectAsState()
    val activeFingerprint by viewModel.activeFingerprint.collectAsState()
    val isTransferring by viewModel.isTransferring.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 28.dp, end = 16.dp, bottom = 16.dp),
    ) {
        // === Row 0: Top Navigation Controls ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 36dp Circular Up-Dir Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .alpha(if (!isAtRoot) 1.0f else 0.4f)
                    .clickable(enabled = !isAtRoot) { viewModel.navigateUp() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_fluent_arrow_back),
                    contentDescription = "Up Directory",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
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
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AnimatedSearchToXIcon(
                        isSearching = searchQuery.isNotEmpty(),
                        onClick = if (searchQuery.isNotEmpty()) {
                            { viewModel.updateSearchQuery("") }
                        } else {
                            null
                        },
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                val hint = if (mode == ExplorerMode.History) {
                                    "Search downloads (${java.io.File(currentLocalPath).name})..."
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
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        },
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
                    .clickable { viewModel.toggleMode() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = if (mode == ExplorerMode.History) painterResource(Res.drawable.ic_fluent_history) else painterResource(Res.drawable.ic_fluent_smartphone),
                    contentDescription = "Toggle Explorer Mode",
                    tint = if (mode == ExplorerMode.Saf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // === Row 1: Middle Grid Area ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (isLoadingSaf) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Loading phone storage...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (displayedFiles.isEmpty()) {
                // Empty State Overlay
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_folder),
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (debouncedQuery.isNotBlank()) "No matching files" else "No transfers yet",
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = if (mode == ExplorerMode.History) "Received files appear here" else "Shared folders from phone appear here",
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(displayedFiles, key = { it.id }) { item ->
                        FileGridItemCard(
                            item = item,
                            isSelected = selectedItemId == item.id,
                            onClick = {
                                if (item.isAddFolderButton) {
                                    viewModel.grantNewFolder()
                                    return@FileGridItemCard
                                }

                                val now = System.currentTimeMillis()
                                if (lastClickedItemId == item.id && now - lastClickTime < 400L) {
                                    // Double-click Action with 400ms delta filter guard
                                    if (mode == ExplorerMode.History) {
                                        handleItemDoubleClick(item, onDrillDown = { viewModel.drillDown(it, item.name, item.uri) })
                                    } else {
                                        // SAF Mode: Drill down into folder or Pull file
                                        if (item.isDirectory) {
                                            viewModel.drillDown(item.path, item.name, item.uri)
                                        } else if (item.uri != null && activeFingerprint.isNotBlank()) {
                                            viewModel.pullSafFile(item.uri!!, item.name, item.size)
                                        }
                                    }
                                } else {
                                    viewModel.selectItem(item.id)
                                    lastClickedItemId = item.id
                                    lastClickTime = now
                                }
                            },
                        )
                    }
                }
            }

            // Fading gradient edge for individual items to scroll into
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )

            // Floating PullProgressDock Toast
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            ) {
                AnimatedVisibility(
                    visible = isTransferring,
                    enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(300)) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { 50 }, animationSpec = tween(300)) + fadeOut(),
                ) {
                    PullProgressDock(
                        clientEngine = clientEngine,
                        onCancel = viewModel::cancelPull,
                    )
                }
            }
        }

        // === Row 2: Bottom Actions Dock ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val sendFilesInteraction = remember { MutableInteractionSource() }
                val sendFilesHovered by sendFilesInteraction.collectIsHoveredAsState()
                val sendFilesScale by animateFloatAsState(targetValue = if (sendFilesHovered) 1.08f else 1.0f, animationSpec = tween(500, easing = DockCardPhysics.HoverEase), label = "sendFilesScale")

                // Send Files Action (Native File Picker)
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = sendFilesScale
                            scaleY = sendFilesScale
                        }
                        .bubbleFluidity()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(
                            interactionSource = sendFilesInteraction,
                            indication = null,
                        ) {
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_file_upload),
                        contentDescription = "Send Files",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    )
                    Text(
                        text = "Send Files",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                val sendFoldersInteraction = remember { MutableInteractionSource() }
                val sendFoldersHovered by sendFoldersInteraction.collectIsHoveredAsState()
                val sendFoldersScale by animateFloatAsState(
                    targetValue = if (sendFoldersHovered) 1.08f else 1.0f,
                    animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                    label = "sendFoldersScale",
                )

                // Send Folders Action (Native Directory Picker)
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = sendFoldersScale
                            scaleY = sendFoldersScale
                        }
                        .bubbleFluidity()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(
                            interactionSource = sendFoldersInteraction,
                            indication = null,
                        ) {
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_folder),
                        contentDescription = "Send Folders",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    )
                    Text(
                        text = "Send Folders",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
