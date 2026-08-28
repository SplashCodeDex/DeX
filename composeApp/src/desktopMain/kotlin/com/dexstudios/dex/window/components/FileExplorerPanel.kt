package com.dexstudios.dex.window.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.frostedSurface
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.components.glass.verticalFadingEdge
import com.dexstudios.dex.core.designsystem.components.overlay.ConfirmationPopup
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_arrow_back
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_close
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_file_upload
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_history
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.core.designsystem.icons.AnimatedSearchToXIcon
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import com.dexstudios.dex.core.network.services.FileExplorerService
import com.dexstudios.dex.desktop.transfer.DesktopFileSendService
import com.dexstudios.dex.window.DockedWindowStateController
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JFileChooser
import kotlin.math.abs
import androidx.compose.ui.input.key.isCtrlPressed as isKeyCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed as isKeyMetaPressed

/**
 * FileExplorerPanel:
 * - Row 0: Top Navigation (36dp UpDir button, 40dp debounced search pill, SAF vs History mode
 *   toggle)
 * - Row 1: LazyVerticalGrid of 100x105dp cards (48x48dp thumbnails, hover lift, press sink, 400ms
 *   double-click guard)
 * - Row 2: Action Dock ("Send Files", "Send Folders", floating PullProgressDock toast with 4dp
 *   emerald progress bar)
 *
 * State and business logic live in [FileExplorerViewModel]; this container only renders.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
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
    fileSender: DesktopFileSendService = koinInject(),
) {
    val viewModel: FileExplorerViewModel = viewModel {
        FileExplorerViewModel(clientEngine, fileExplorerService, discoveryEngine)
    }

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var lastClickTime by remember { mutableStateOf(0L) }
    var lastClickedItemId by remember { mutableStateOf<String?>(null) }
    var isCanvasContextMenuOpen by remember { mutableStateOf(false) }
    var canvasMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var isClearHistoryConfirmOpen by remember { mutableStateOf(false) }
    var isBatchDeleteConfirmOpen by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ExplorerFileItem?>(null) }

    val itemBoundsMap = remember { mutableStateMapOf<String, Rect>() }
    var gridBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
    var currentDragOffset by remember { mutableStateOf<Offset?>(null) }
    var isDraggingMarquee by remember { mutableStateOf(false) }
    var marqueeInitialSelection by remember { mutableStateOf<Set<String>>(emptySet()) }

    val mode by viewModel.mode.collectAsState()
    val currentLocalPath by viewModel.currentLocalPath.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val debouncedQuery by viewModel.debouncedQuery.collectAsState()
    val selectedItemIds by viewModel.selectedItemIds.collectAsState()
    val selectedItemId by viewModel.selectedItemId.collectAsState()
    val displayedFiles by viewModel.displayedFiles.collectAsState()
    val isAtRoot by viewModel.isAtRoot.collectAsState()
    val isLoadingSaf by viewModel.isLoadingSaf.collectAsState()
    val isListingLoading by viewModel.isListingLoading.collectAsState()
    val safBreadcrumb by viewModel.safBreadcrumb.collectAsState()
    val activePhone by viewModel.activePhone.collectAsState()
    val activeFingerprint by viewModel.activeFingerprint.collectAsState()
    val isTransferring by viewModel.isTransferring.collectAsState()
    val explorerError by viewModel.explorerError.collectAsState()
    val quickLookItem by viewModel.quickLookItem.collectAsState()

    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // The panel root owns the keyboard surface (Quick Look, select-all, Escape, Delete), so it
    // must always hold focus while the drawer is open. A single Unit-keyed request races the
    // drawer's enter animation and silently loses, leaving keys dead until a file click — so
    // re-acquire focus on every expansion change and again when the Quick Look modal closes.
    // While the modal is open the root's onPreviewKeyEvent keeps handling all keys; the modal
    // itself never claims focus.
    LaunchedEffect(controller?.isExpanded, controller?.expandedPanel, quickLookItem) {
        if (controller?.isExpanded != true || quickLookItem != null) return@LaunchedEffect
        withFrameNanos { }
        focusRequester.requestFocus()
    }

    LaunchedEffect(displayedFiles) {
        val currentIds = displayedFiles.map { it.id }.toSet()
        itemBoundsMap.keys.retainAll(currentIds)
    }

    val isPhoneConnected = remember(activeFingerprint, activePhone) {
        activeFingerprint.isNotBlank() && (WebSocketConnectionManager.isConnected(activeFingerprint) || activePhone != null)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val isSpace = event.key == Key.Spacebar || event.utf16CodePoint == ' '.code
                    // Any open confirmation dialog outranks Quick Look (zIndex 99 vs 95) —
                    // Space must never lift the preview above or beneath an active prompt.
                    val isConfirmDialogOpen = isClearHistoryConfirmOpen || isBatchDeleteConfirmOpen || itemToDelete != null

                    if (isSpace && !isSearchFocused && !isConfirmDialogOpen) {
                        viewModel.toggleQuickLook()
                        return@onPreviewKeyEvent true
                    }

                    if (quickLookItem != null) {
                        when (event.key) {
                            Key.Escape -> {
                                viewModel.closeQuickLook()
                                return@onPreviewKeyEvent true
                            }

                            Key.DirectionRight, Key.DirectionDown -> {
                                viewModel.quickLookNext()
                                return@onPreviewKeyEvent true
                            }

                            Key.DirectionLeft, Key.DirectionUp -> {
                                viewModel.quickLookPrevious()
                                return@onPreviewKeyEvent true
                            }

                            Key.Enter -> {
                                quickLookItem?.let { ql ->
                                    if (ql.isDirectory) {
                                        viewModel.drillDown(ql.path, ql.name, ql.uri)
                                    } else {
                                        openFileNative(ql.path)
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                    } else if (!isSearchFocused) {
                        if ((event.isKeyCtrlPressed || event.isKeyMetaPressed) && event.key == Key.A) {
                            if (mode == ExplorerMode.History) {
                                viewModel.selectAll()
                                return@onPreviewKeyEvent true
                            }
                        } else if (event.key == Key.Escape) {
                            if (mode == ExplorerMode.History) {
                                viewModel.clearSelection()
                                return@onPreviewKeyEvent true
                            }
                        } else if (event.key == Key.Delete || event.key == Key.Backspace) {
                            if (mode == ExplorerMode.History && selectedItemIds.isNotEmpty()) {
                                if (selectedItemIds.size > 1) {
                                    isBatchDeleteConfirmOpen = true
                                } else {
                                    val item = displayedFiles.find { it.id in selectedItemIds }
                                    if (item != null) itemToDelete = item
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                }
                false
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        Column(
            modifier =
            Modifier.fillMaxSize().padding(start = 24.dp, top = 28.dp, end = 16.dp, bottom = 16.dp),
        ) {
            // === Row 0: Top Navigation Controls ===
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val upDirInteraction = remember { MutableInteractionSource() }
                val isUpDirHovered by upDirInteraction.collectIsHoveredAsState()
                val upDirScale by
                    animateFloatAsState(
                        targetValue = if (isUpDirHovered) 1.08f else 1.0f,
                        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                        label = "upDirScale",
                    )
                val upDirTranslateY by
                    animateDpAsState(
                        targetValue = if (isUpDirHovered) (-3).dp else 0.dp,
                        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                        label = "upDirTransY",
                    )

                // 40dp Circular Up-Dir Button
                Box(
                    modifier =
                    Modifier.zIndex(if (isUpDirHovered) 1f else 0f)
                        .graphicsLayer {
                            scaleX = upDirScale
                            scaleY = upDirScale
                            translationY = upDirTranslateY.toPx()
                        }
                        .size(40.dp)
                        .bubbleFluidity()
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(20.dp),
                            spotColor = Color.Black.copy(alpha = 0.48f),
                            ambientColor = Color.Black.copy(alpha = 0.26f),
                        )
                        .frostedSurface(
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            opacity = 1.0f,
                        )
                        .alpha(if (!isAtRoot) 1.0f else 0.4f)
                        .clickable(
                            interactionSource = upDirInteraction,
                            indication = null,
                            enabled = !isAtRoot,
                        ) {
                            viewModel.navigateUp()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_arrow_back),
                        contentDescription = "Up Directory",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                val searchInteraction = remember { MutableInteractionSource() }
                val isSearchHovered by searchInteraction.collectIsHoveredAsState()
                val searchScale by
                    animateFloatAsState(
                        targetValue = if (isSearchHovered) 1.05f else 1.0f,
                        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                        label = "searchScale",
                    )
                val searchTranslateY by
                    animateDpAsState(
                        targetValue = if (isSearchHovered) (-3).dp else 0.dp,
                        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                        label = "searchTransY",
                    )

                // Current Path / Folder Indicator or Search Pill
                Box(
                    modifier =
                    Modifier.zIndex(if (isSearchHovered) 1f else 0f)
                        .graphicsLayer {
                            scaleX = searchScale
                            scaleY = searchScale
                            translationY = searchTranslateY.toPx()
                        }
                        .weight(2f)
                        .padding(horizontal = 12.dp)
                        .height(40.dp)
                        .bubbleFluidity(targetScale = 1.10f, pullFactor = 0.15f)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(30.dp),
                            spotColor = Color.Black.copy(alpha = 0.48f),
                            ambientColor = Color.Black.copy(alpha = 0.26f),
                        )
                        .frostedSurface(
                            shape = RoundedCornerShape(30.dp),
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            opacity = 1.0f,
                        )
                        .hoverable(searchInteraction)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AnimatedSearchToXIcon(
                            isSearching = searchQuery.isNotEmpty(),
                            onClick =
                            if (searchQuery.isNotEmpty()) {
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
                            textStyle =
                            TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isSearchFocused = it.isFocused },
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    val hint =
                                        if (mode == ExplorerMode.History) {
                                            "Search downloads (${java.io.File(currentLocalPath).name})..."
                                        } else {
                                            val currentSafName =
                                                safBreadcrumb.lastOrNull()?.first
                                                    ?: (activePhone?.info?.alias ?: "Phone")
                                            "Search $currentSafName..."
                                        }
                                    Text(
                                        text = hint,
                                        color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.7f,
                                        ),
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

                val toggleInteraction = remember { MutableInteractionSource() }
                val isToggleHovered by toggleInteraction.collectIsHoveredAsState()
                val toggleScale by
                    animateFloatAsState(
                        targetValue = if (isToggleHovered) 1.08f else 1.0f,
                        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                        label = "toggleScale",
                    )
                val toggleTranslateY by
                    animateDpAsState(
                        targetValue = if (isToggleHovered) (-3).dp else 0.dp,
                        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                        label = "toggleTransY",
                    )

                // 40dp Mode Toggle Button (SAF Phone vs PC History)
                Box(
                    modifier =
                    Modifier.zIndex(if (isToggleHovered) 1f else 0f)
                        .graphicsLayer {
                            scaleX = toggleScale
                            scaleY = toggleScale
                            translationY = toggleTranslateY.toPx()
                        }
                        .size(40.dp)
                        .bubbleFluidity()
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(20.dp),
                            spotColor = Color.Black.copy(alpha = 0.48f),
                            ambientColor = Color.Black.copy(alpha = 0.26f),
                        )
                        .frostedSurface(
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            opacity = 1.0f,
                        )
                        .clickable(interactionSource = toggleInteraction, indication = null) {
                            viewModel.toggleMode()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter =
                        if (mode == ExplorerMode.History) {
                            painterResource(Res.drawable.ic_fluent_history)
                        } else {
                            painterResource(Res.drawable.ic_fluent_smartphone)
                        },
                        contentDescription = "Toggle Explorer Mode",
                        tint =
                        if (mode == ExplorerMode.Saf) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Inline error banner (connection / load failures)
            androidx.compose.animation.AnimatedVisibility(
                visible = explorerError != null,
            ) {
                Row(
                    modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = explorerError.orEmpty(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_close),
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp).clickable { viewModel.clearError() },
                    )
                }
            }
            if (explorerError != null) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // === Row 1: Middle Grid Area (starts directly below the navigation bar) ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coordinates ->
                        gridBoundsInRoot = coordinates.boundsInRoot()
                    }
                    .pointerInput(mode) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                val change = event.changes.firstOrNull() ?: continue

                                if (event.type == PointerEventType.Press) {
                                    // Clear search focus FIRST — clearFocus() wipes the whole
                                    // tree and would undo a requestFocus() issued before it.
                                    if (isSearchFocused) {
                                        focusManager.clearFocus()
                                    }
                                    focusRequester.requestFocus()
                                    if (!change.isConsumed) {
                                        if (event.button == PointerButton.Secondary) {
                                            if (mode == ExplorerMode.History) {
                                                canvasMenuOffset = DpOffset(change.position.x.toDp(), change.position.y.toDp())
                                                isCanvasContextMenuOpen = true
                                            }
                                        } else if (event.button == PointerButton.Primary) {
                                            if (mode == ExplorerMode.History) {
                                                val isCtrlOrMeta = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                                                dragStartOffset = change.position
                                                currentDragOffset = change.position
                                                isDraggingMarquee = false
                                                marqueeInitialSelection = if (isCtrlOrMeta) selectedItemIds else emptySet()
                                                if (!isCtrlOrMeta) {
                                                    viewModel.clearSelection()
                                                }
                                            }
                                        }
                                    }
                                } else if (event.type == PointerEventType.Move && dragStartOffset != null) {
                                    val start = dragStartOffset!!
                                    val current = change.position
                                    currentDragOffset = current
                                    val dragDist = (current - start).getDistance()
                                    if (dragDist > 6f) {
                                        isDraggingMarquee = true
                                        val rootPos = gridBoundsInRoot?.topLeft ?: Offset.Zero
                                        val startRoot = rootPos + start
                                        val curRoot = rootPos + current
                                        val marqueeRectInRoot = Rect(
                                            minOf(startRoot.x, curRoot.x),
                                            minOf(startRoot.y, curRoot.y),
                                            maxOf(startRoot.x, curRoot.x),
                                            maxOf(startRoot.y, curRoot.y),
                                        )
                                        val interceptedIds = itemBoundsMap.filter { (_, rect) ->
                                            marqueeRectInRoot.overlaps(rect)
                                        }.keys
                                        viewModel.setSelectedIds(marqueeInitialSelection + interceptedIds)
                                    }
                                } else if (event.type == PointerEventType.Release) {
                                    dragStartOffset = null
                                    currentDragOffset = null
                                    isDraggingMarquee = false
                                }
                            }
                        }
                    },
            ) {
                // Rubberband Marquee Selection Box Overlay
                if (isDraggingMarquee && dragStartOffset != null && currentDragOffset != null) {
                    val start = dragStartOffset!!
                    val end = currentDragOffset!!
                    val left = minOf(start.x, end.x)
                    val top = minOf(start.y, end.y)
                    val width = abs(end.x - start.x)
                    val height = abs(end.y - start.y)
                    val primaryColor = MaterialTheme.colorScheme.primary

                    if (width > 2f && height > 2f) {
                        Canvas(modifier = Modifier.fillMaxSize().zIndex(50f)) {
                            drawRoundRect(
                                color = primaryColor.copy(alpha = 0.16f),
                                topLeft = Offset(left, top),
                                size = Size(width, height),
                                cornerRadius = CornerRadius(6.dp.toPx()),
                            )
                            drawRoundRect(
                                color = primaryColor.copy(alpha = 0.70f),
                                topLeft = Offset(left, top),
                                size = Size(width, height),
                                cornerRadius = CornerRadius(6.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                }

                if (mode == ExplorerMode.History) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(canvasMenuOffset.x, canvasMenuOffset.y)
                            .size(0.dp),
                    ) {
                        HistoryCanvasContextMenu(
                            expanded = isCanvasContextMenuOpen,
                            onDismissRequest = { isCanvasContextMenuOpen = false },
                            offset = DpOffset.Zero,
                            onOpenDownloadsFolder = {
                                openFolderAndSelectNative(getDeXDownloadDirectory())
                            },
                            onRefresh = {
                                viewModel.refreshHistory()
                            },
                            onClearAllHistory = {
                                isClearHistoryConfirmOpen = true
                            },
                        )
                    }
                }

                if (isListingLoading && displayedFiles.isEmpty()) {
                    ExplorerSkeletonGrid(
                        modifier =
                        Modifier.fillMaxSize()
                            .verticalFadingEdge(topFadeHeight = 16.dp, bottomFadeHeight = 16.dp),
                    )
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
                            text =
                            if (debouncedQuery.isNotBlank()) {
                                "No matching files"
                            } else {
                                "No transfers yet"
                            },
                            fontSize = 14.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text =
                            if (mode == ExplorerMode.History) {
                                "Received files appear here"
                            } else {
                                "Shared folders from phone appear here"
                            },
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier =
                        Modifier.fillMaxSize()
                            .verticalFadingEdge(topFadeHeight = 16.dp, bottomFadeHeight = 16.dp),
                    ) {
                        items(displayedFiles, key = { it.id }) { item ->
                            val isSelected = item.id in selectedItemIds
                            FileGridItemCard(
                                item = item,
                                isSelected = isSelected,
                                mode = mode,
                                isPhoneConnected = isPhoneConnected,
                                selectedCount = if (isSelected) selectedItemIds.size else 1,
                                selectedFilesProvider = {
                                    val ids = if (isSelected) selectedItemIds else setOf(item.id)
                                    displayedFiles
                                        .filter { it.id in ids && !it.isAddFolderButton }
                                        .map { java.io.File(it.path) }
                                        .filter { it.exists() }
                                },
                                onPositioned = { id, bounds ->
                                    itemBoundsMap[id] = bounds
                                },
                                onClick = { isCtrlOrMeta, isShift ->
                                    // Clear search focus FIRST — clearFocus() wipes the whole
                                    // tree and would undo a requestFocus() issued before it.
                                    if (isSearchFocused) {
                                        focusManager.clearFocus()
                                    }
                                    focusRequester.requestFocus()
                                    if (item.isAddFolderButton) {
                                        viewModel.grantNewFolder()
                                        return@FileGridItemCard
                                    }

                                    val now = System.currentTimeMillis()
                                    if (lastClickedItemId == item.id && now - lastClickTime < 400L && !isCtrlOrMeta && !isShift) {
                                        lastClickedItemId = null
                                        lastClickTime = 0L
                                        // Double-click Action with 400ms delta filter guard
                                        if (mode == ExplorerMode.History) {
                                            handleItemDoubleClick(
                                                item,
                                                onDrillDown = {
                                                    viewModel.drillDown(it, item.name, item.uri)
                                                },
                                            )
                                        } else {
                                            // SAF Mode: Drill down into folder or Pull file
                                            if (item.isDirectory) {
                                                viewModel.drillDown(item.path, item.name, item.uri)
                                            } else if (item.uri != null && activeFingerprint.isNotBlank()) {
                                                viewModel.pullSafFile(
                                                    item.uri,
                                                    item.name,
                                                    item.size,
                                                )
                                            }
                                        }
                                    } else {
                                        lastClickedItemId = item.id
                                        lastClickTime = now
                                        if (mode == ExplorerMode.History) {
                                            when {
                                                isCtrlOrMeta -> viewModel.toggleSelection(item.id)
                                                isShift -> viewModel.selectRange(item.id)
                                                else -> viewModel.selectSingle(item.id)
                                            }
                                        } else {
                                            viewModel.selectSingle(item.id)
                                        }
                                    }
                                },
                                onSecondaryClick = {
                                    if (mode == ExplorerMode.History) {
                                        if (item.id !in selectedItemIds) {
                                            viewModel.selectSingle(item.id)
                                        }
                                    } else {
                                        viewModel.selectSingle(item.id)
                                    }
                                },
                                onOpen = {
                                    val targetItems = if (isSelected && selectedItemIds.size > 1) {
                                        displayedFiles.filter { it.id in selectedItemIds && !it.isAddFolderButton }
                                    } else {
                                        listOf(item)
                                    }
                                    targetItems.forEach { t ->
                                        if (t.isDirectory) {
                                            viewModel.drillDown(t.path, t.name, t.uri)
                                        } else {
                                            openFileNative(t.path)
                                        }
                                    }
                                },
                                onOpenLocation = {
                                    openFolderAndSelectNative(item.path)
                                },
                                onSendToPhone = {
                                    val targetItems = if (isSelected && selectedItemIds.size > 1) {
                                        displayedFiles.filter { it.id in selectedItemIds && !it.isAddFolderButton }
                                    } else {
                                        listOf(item)
                                    }
                                    val files = targetItems.filter { !it.isDirectory }.map { java.io.File(it.path) }
                                    val folders = targetItems.filter { it.isDirectory }.map { java.io.File(it.path) }
                                    coroutineScope.launch(Dispatchers.IO) {
                                        if (files.isNotEmpty()) fileSender.sendFiles(files)
                                        if (folders.isNotEmpty()) fileSender.sendFolders(folders)
                                    }
                                },
                                onDelete = {
                                    if (isSelected && selectedItemIds.size > 1) {
                                        isBatchDeleteConfirmOpen = true
                                    } else {
                                        itemToDelete = item
                                    }
                                },
                            )
                        }
                    }
                }

                // Floating PullProgressDock Toast
                Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) {
                    AnimatedVisibility(
                        visible = isTransferring,
                        enter =
                        slideInVertically(
                            initialOffsetY = { 50 },
                            animationSpec = DockCardAnimations.LinearSlideSpec,
                        ) + fadeIn(),
                        exit =
                        slideOutVertically(
                            targetOffsetY = { 50 },
                            animationSpec = DockCardAnimations.LinearSlideSpec,
                        ) + fadeOut(),
                    ) {
                        PullProgressDock(
                            clientEngine = clientEngine,
                            fileExplorerService = fileExplorerService,
                            onCancel = viewModel::cancelPull,
                        )
                    }
                }
            }

            // === Row 2: Bottom Actions Dock ===
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sendFilesInteraction = remember { MutableInteractionSource() }
                    val sendFilesHovered by sendFilesInteraction.collectIsHoveredAsState()
                    val sendFilesScale by
                        animateFloatAsState(
                            targetValue = if (sendFilesHovered) 1.08f else 1.0f,
                            animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                            label = "sendFilesScale",
                        )
                    val sendFilesTranslateY by
                        animateDpAsState(
                            targetValue = if (sendFilesHovered) (-3).dp else 0.dp,
                            animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                            label = "sendFilesTransY",
                        )

                    // Send Files Action (Native File Picker)
                    Row(
                        modifier =
                        Modifier.zIndex(if (sendFilesHovered) 1f else 0f)
                            .graphicsLayer {
                                scaleX = sendFilesScale
                                scaleY = sendFilesScale
                                translationY = sendFilesTranslateY.toPx()
                            }
                            .bubbleFluidity()
                            .frostedSurface(
                                shape = RoundedCornerShape(10.dp),
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                opacity = 1.0f,
                            )
                            .clickable(
                                interactionSource = sendFilesInteraction,
                                indication = null,
                            ) {
                                controller?.isModalDialogOpen = true
                                onSendFiles()
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val picked = pickFilesForSend("Select Files to Send")
                                        if (picked.isNotEmpty()) {
                                            fileSender.sendFiles(picked)
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
                    val sendFoldersScale by
                        animateFloatAsState(
                            targetValue = if (sendFoldersHovered) 1.08f else 1.0f,
                            animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                            label = "sendFoldersScale",
                        )
                    val sendFoldersTranslateY by
                        animateDpAsState(
                            targetValue = if (sendFoldersHovered) (-3).dp else 0.dp,
                            animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
                            label = "sendFoldersTransY",
                        )

                    // Send Folders Action (Native Directory Picker)
                    Row(
                        modifier =
                        Modifier.zIndex(if (sendFoldersHovered) 1f else 0f)
                            .graphicsLayer {
                                scaleX = sendFoldersScale
                                scaleY = sendFoldersScale
                                translationY = sendFoldersTranslateY.toPx()
                            }
                            .bubbleFluidity()
                            .frostedSurface(
                                shape = RoundedCornerShape(10.dp),
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                opacity = 1.0f,
                            )
                            .clickable(
                                interactionSource = sendFoldersInteraction,
                                indication = null,
                            ) {
                                controller?.isModalDialogOpen = true
                                onSendFolders()
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val folder = pickFolderForSend("Select Folder to Send")
                                        if (folder != null) {
                                            fileSender.sendFolders(listOf(folder))
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

        if (isClearHistoryConfirmOpen || isBatchDeleteConfirmOpen || itemToDelete != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .zIndex(99f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            isClearHistoryConfirmOpen = false
                            isBatchDeleteConfirmOpen = false
                            itemToDelete = null
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isClearHistoryConfirmOpen) {
                    ConfirmationPopup(
                        title = "Clear Transfer History",
                        message = "Are you sure you want to clear all transfer history logs? Your files on disk will not be deleted.",
                        confirmButtonText = "Clear History",
                        cancelButtonText = "Cancel",
                        isDestructive = true,
                        onConfirm = {
                            viewModel.clearAllHistory()
                            isClearHistoryConfirmOpen = false
                        },
                        onCancel = {
                            isClearHistoryConfirmOpen = false
                        },
                        onDismiss = {
                            isClearHistoryConfirmOpen = false
                        },
                    )
                } else if (isBatchDeleteConfirmOpen) {
                    val count = selectedItemIds.size
                    ConfirmationPopup(
                        title = "Delete $count Items from History",
                        message = "Are you sure you want to remove $count items from history? Their physical files on disk will not be deleted.",
                        confirmButtonText = "Delete All ($count)",
                        cancelButtonText = "Cancel",
                        isDestructive = true,
                        onConfirm = {
                            viewModel.removeSelectedFromHistory()
                            isBatchDeleteConfirmOpen = false
                        },
                        onCancel = {
                            isBatchDeleteConfirmOpen = false
                        },
                        onDismiss = {
                            isBatchDeleteConfirmOpen = false
                        },
                    )
                } else if (itemToDelete != null) {
                    val target = itemToDelete!!
                    ConfirmationPopup(
                        title = "Delete from History",
                        message = "Are you sure you want to delete \"${target.name}\" from history? The physical file on disk will not be removed.",
                        confirmButtonText = "Delete",
                        cancelButtonText = "Cancel",
                        isDestructive = true,
                        onConfirm = {
                            viewModel.removeFromHistory(target.id, target.path)
                            itemToDelete = null
                        },
                        onCancel = {
                            itemToDelete = null
                        },
                        onDismiss = {
                            itemToDelete = null
                        },
                    )
                }
            }
        }

        // Quick Look Modal Overlay
        AnimatedVisibility(
            visible = quickLookItem != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize().zIndex(95f),
        ) {
            quickLookItem?.let { qlItem ->
                val validFiles = displayedFiles.filterNot { it.isAddFolderButton }
                val idx = validFiles.indexOfFirst { it.id == qlItem.id }.coerceAtLeast(0)
                QuickLookModal(
                    item = qlItem,
                    currentIndex = idx,
                    totalCount = validFiles.size.coerceAtLeast(1),
                    isPhoneConnected = isPhoneConnected,
                    onDismiss = { viewModel.closeQuickLook() },
                    onOpenNative = {
                        if (qlItem.isDirectory) {
                            viewModel.drillDown(qlItem.path, qlItem.name, qlItem.uri)
                        } else {
                            openFileNative(qlItem.path)
                        }
                    },
                    onOpenLocation = {
                        openFolderAndSelectNative(qlItem.path)
                    },
                    onNext = { viewModel.quickLookNext() },
                    onPrevious = { viewModel.quickLookPrevious() },
                    onSendToPhone = {
                        coroutineScope.launch(Dispatchers.IO) {
                            if (qlItem.isDirectory) {
                                fileSender.sendFolders(listOf(java.io.File(qlItem.path)))
                            } else {
                                fileSender.sendFiles(listOf(java.io.File(qlItem.path)))
                            }
                        }
                    },
                )
            }
        }
    }
}

private suspend fun pickFilesForSend(title: String): List<java.io.File> = kotlinx.coroutines.withContext(Dispatchers.IO) {
    try {
        val holder = arrayOfNulls<Array<java.io.File>>(1)
        EventQueue.invokeAndWait {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            dialog.isMultipleMode = true
            dialog.isVisible = true
            holder[0] = dialog.files
        }
        holder[0]?.toList() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

private suspend fun pickFolderForSend(title: String): java.io.File? = kotlinx.coroutines.withContext(Dispatchers.IO) {
    try {
        val holder = arrayOfNulls<java.io.File>(1)
        EventQueue.invokeAndWait {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.dialogTitle = title
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                holder[0] = chooser.selectedFile
            }
        }
        holder[0]
    } catch (e: Exception) {
        null
    }
}
