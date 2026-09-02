package com.dexstudios.dex.ui.main.components
import timber.log.Timber

import com.dexstudios.dex.network.checkHasMediaPermission
import com.dexstudios.dex.network.getMediaPermissions
import com.dexstudios.dex.ui.util.Formatters

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import androidx.compose.foundation.isSystemInDarkTheme
import com.dexstudios.dex.ui.components.LiquidGlassButton
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecentMediaItem(
    val uri: Uri,
    val name: String,
    val size: Long = 0L,
    val dateAdded: Long = 0L,
    val isVideo: Boolean = false,
)

data class RecentAudioItem(
    val uri: Uri,
    val name: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val albumArtUri: Uri?,
)

data class RecentFileItem(
    val uri: Uri,
    val name: String,
    val size: Long = 0L,
    val dateAdded: Long = 0L,
    val mimeType: String = "",
    val extension: String = "",
)

enum class MediaTrayTab {
    PhotosAndVideos,
    Audio,
    Files
}

@Composable
fun MediaPickerTray(
    backdrop: Backdrop?,
    currentTab: MediaTrayTab = MediaTrayTab.PhotosAndVideos,
    onTabChange: ((MediaTrayTab) -> Unit)? = null,
    onSend: (List<Uri>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val selectedUris = remember { mutableStateListOf<Uri>() }

    val pagerState = rememberPagerState(
        initialPage = currentTab.ordinal,
        pageCount = { MediaTrayTab.entries.size }
    )

    // Synchronize pager when currentTab changes from outside (e.g. navbar click)
    LaunchedEffect(currentTab) {
        if (pagerState.currentPage != currentTab.ordinal) {
            pagerState.animateScrollToPage(currentTab.ordinal)
        }
    }

    // Synchronize navbar when user swipes the pager
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val targetTab = MediaTrayTab.entries.getOrNull(page) ?: MediaTrayTab.PhotosAndVideos
            if (targetTab != currentTab) {
                onTabChange?.invoke(targetTab)
            }
        }
    }

    // Media, Audio & Files state
    var mediaItems by remember { mutableStateOf<List<RecentMediaItem>>(emptyList()) }
    var audioItems by remember { mutableStateOf<List<RecentAudioItem>>(emptyList()) }
    var fileItems by remember { mutableStateOf<List<RecentFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(checkHasMediaPermission(context)) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // Audio preview player state
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef.value?.stop()
                mediaPlayerRef.value?.release()
                mediaPlayerRef.value = null
            } catch (_: Exception) {}
        }
    }

    // Permission launcher for accessing device media and storage
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        hasPermission = granted
        if (granted) {
            reloadTrigger++
        }
    }

    // Storage Document Picker for deep browsing
    val systemDocPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { if (!selectedUris.contains(it)) selectedUris.add(it) }
        }
    }

    // Camera capture launcher
    val cameraCapture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val tempFile = java.io.File(context.cacheDir, "dex_camera_${System.currentTimeMillis()}.jpg")
                tempFile.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                }
                val photoUri = Uri.fromFile(tempFile)
                if (!selectedUris.contains(photoUri)) {
                    selectedUris.add(photoUri)
                }
            } catch (e: Exception) {
                Timber.e(e, "Operation failed")
            }
        }
    }

    // Load actual recent photos/videos, audio files, and documents from Android MediaStore
    LaunchedEffect(hasPermission, reloadTrigger) {
        if (hasPermission) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val loadedMedia = loadRecentMedia(context)
                val loadedAudio = loadRecentAudio(context)
                val loadedFiles = loadRecentFiles(context)
                mediaItems = loadedMedia
                audioItems = loadedAudio
                fileItems = loadedFiles
            }
            isLoading = false
        } else {
            mediaItems = emptyList()
            audioItems = emptyList()
            fileItems = emptyList()
        }
    }

    fun toggleAudioPreview(item: RecentAudioItem) {
        if (playingUri == item.uri) {
            try {
                mediaPlayerRef.value?.stop()
                mediaPlayerRef.value?.release()
                mediaPlayerRef.value = null
            } catch (_: Exception) {}
            playingUri = null
        } else {
            try {
                mediaPlayerRef.value?.stop()
                mediaPlayerRef.value?.release()
                val player = MediaPlayer().apply {
                    setDataSource(context, item.uri)
                    prepare()
                    start()
                    setOnCompletionListener {
                        playingUri = null
                        mediaPlayerRef.value?.release()
                        mediaPlayerRef.value = null
                    }
                }
                mediaPlayerRef.value = player
                playingUri = item.uri
            } catch (e: Exception) {
                Timber.e(e, "Audio preview playback failed")
                playingUri = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {


        // --- 2. Main Tray Body (Photos/Videos Grid vs. Audio List vs. Files List) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (!hasPermission) {
                // Permission Request Callout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Device Storage Access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Allow access to easily select and share your photos, audio, and files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            permissionLauncher.launch(getMediaPermissions())
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Grant Access",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { it },
                ) { page ->
                    when (MediaTrayTab.entries[page]) {
                        // --- Tab 1: Photos & Videos ---
                        MediaTrayTab.PhotosAndVideos -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 100.dp),
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                            ) {
                                // 1. Clever First Tile: Camera Capture Tile
                                item(key = "camera_capture_card") {
                                    CameraCaptureCard(
                                        onClick = { cameraCapture.launch(null) },
                                    )
                                }

                                // 2. Storage Media Items
                                items(mediaItems, key = { it.uri.toString() }) { item ->
                                    val isItemSelected = selectedUris.contains(item.uri)

                                    MediaThumbnailCard(
                                        item = item,
                                        isSelected = isItemSelected,
                                        onToggle = {
                                            if (isItemSelected) selectedUris.remove(item.uri)
                                            else selectedUris.add(item.uri)
                                        },
                                    )
                                }
                            }
                        }

                        // --- Tab 2: Audio Tracks ---
                        MediaTrayTab.Audio -> {
                            if (audioItems.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbols.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "No audio files found on device",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    items(audioItems, key = { it.uri.toString() }) { audio ->
                                        val isSelected = selectedUris.contains(audio.uri)
                                        val isPlaying = (playingUri == audio.uri)

                                        AudioTrackCard(
                                            item = audio,
                                            isSelected = isSelected,
                                            isPlaying = isPlaying,
                                            onToggleSelect = {
                                                if (isSelected) selectedUris.remove(audio.uri)
                                                else selectedUris.add(audio.uri)
                                            },
                                            onTogglePlay = {
                                                toggleAudioPreview(audio)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // --- Tab 3: Files & Documents ---
                        MediaTrayTab.Files -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                // Deep Browse Storage action header
                                item(key = "browse_storage_header") {
                                    BrowseStorageHeaderCard(
                                        onClick = { systemDocPicker.launch(arrayOf("*/*")) }
                                    )
                                }

                                if (fileItems.isEmpty()) {
                                    item(key = "empty_files_state") {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 32.dp)
                                        ) {
                                            Icon(
                                                imageVector = MaterialSymbols.Article,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "No recent files found in storage",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                } else {
                                    items(fileItems, key = { it.uri.toString() }) { file ->
                                        val isSelected = selectedUris.contains(file.uri)

                                        FileDocumentCard(
                                            item = file,
                                            isSelected = isSelected,
                                            onToggleSelect = {
                                                if (isSelected) selectedUris.remove(file.uri)
                                                else selectedUris.add(file.uri)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Floating Bottom Send Action Bar ---
        AnimatedVisibility(
            visible = selectedUris.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassButton(
                    text = "Send ${selectedUris.size} item${if (selectedUris.size > 1) "s" else ""}",
                    onClick = {
                        try {
                            mediaPlayerRef.value?.stop()
                            mediaPlayerRef.value?.release()
                            mediaPlayerRef.value = null
                            playingUri = null
                        } catch (_: Exception) {}
                        onSend(selectedUris.toList())
                    },
                    backdrop = backdrop,
                    icon = MaterialSymbols.Send,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }
        }
    }
}

/**
 * Modern Camera Capture Tile placed as the first box in the Photos grid.
 */
@Composable
private fun CameraCaptureCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemShape = RoundedCornerShape(22.dp)
    val isDark = isSystemInDarkTheme()

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .bubbleFluidity(targetScale = 1.04f, pullFactor = 0.05f)
                .clip(itemShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = itemShape,
                )
                .background(
                    if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MaterialSymbols.PhotoCamera,
                    contentDescription = "Camera",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Camera",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Header card to open the system Document Picker to browse all phone directories.
 */
@Composable
private fun BrowseStorageHeaderCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .bubbleFluidity(targetScale = 1.02f, pullFactor = 0.03f)
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MaterialSymbols.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Browse Storage...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Pick files from Downloads, Drive, SD Card",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * High-performance File/Document Card in the Sheet (Standard Material 3, No Liquid Glass overhead).
 */
@Composable
private fun FileDocumentCard(
    item: RecentFileItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val ext = item.extension
    val badgeBgColor = when (ext) {
        "PDF" -> Color(0xFFE53935)
        "ZIP", "RAR", "7Z", "TAR", "GZ" -> Color(0xFFFB8C00)
        "DOC", "DOCX", "TXT", "MD", "RTF" -> Color(0xFF1E88E5)
        "XLS", "XLSX", "CSV" -> Color(0xFF43A047)
        "PPT", "PPTX", "KEY" -> Color(0xFFF4511E)
        "APK", "AAB" -> Color(0xFF00897B)
        "KT", "JAVA", "PY", "JS", "TS", "JSON", "XML", "HTML", "CSS", "CPP", "C", "SH" -> Color(0xFF5E35B1)
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .bubbleFluidity(targetScale = 1.02f, pullFactor = 0.03f)
            .clip(cardShape)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                shape = cardShape
            )
            .background(bgColor)
            .clickable(onClick = onToggleSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Left: File Extension / Type Badge
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(badgeBgColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            if (ext.isNotEmpty() && ext.length <= 4) {
                Text(
                    text = ext,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = badgeBgColor
                )
            } else {
                Icon(
                    imageVector = MaterialSymbols.Article,
                    contentDescription = null,
                    tint = badgeBgColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 2. Middle: File Name, Size & Date
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            val sizeFormatted = Formatters.formatBytes(item.size)
            val dateFormatted = formatDate(item.dateAdded)
            Text(
                text = if (dateFormatted.isNotEmpty()) "$sizeFormatted • $dateFormatted" else sizeFormatted,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 3. Right: Selection Checkbox Badge
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 0.dp else 1.5.dp,
                    color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = MaterialSymbols.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * High-performance Custom Audio Track Card in the Sheet (Standard Material 3, No Liquid Glass overhead).
 */
@Composable
private fun AudioTrackCard(
    item: RecentAudioItem,
    isSelected: Boolean,
    isPlaying: Boolean,
    onToggleSelect: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .bubbleFluidity(targetScale = 1.02f, pullFactor = 0.03f)
            .clip(cardShape)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                shape = cardShape
            )
            .background(bgColor)
            .clickable(onClick = onToggleSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Left: Album Art or Fallback Music Icon
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (item.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.albumArtUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = MaterialSymbols.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 2. Middle: Track Title, Artist, Duration & File Size
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.title.ifEmpty { item.name },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            val durationFormatted = Formatters.formatDuration(item.durationMs)
            val sizeFormatted = Formatters.formatBytes(item.size)
            Text(
                text = "${item.artist} • $durationFormatted • $sizeFormatted",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 3. Right: Mini Preview Play/Pause Button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) MaterialSymbols.Pause else MaterialSymbols.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Preview Play",
                tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 4. Selection Checkbox Badge
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 0.dp else 1.5.dp,
                    color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = MaterialSymbols.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Card for each photo/video media item in the grid.
 */
@Composable
private fun MediaThumbnailCard(
    item: RecentMediaItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .bubbleFluidity(targetScale = 1.04f, pullFactor = 0.05f)
            .clip(itemShape)
            .border(
                width = if (isSelected) 2.5.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                shape = itemShape
            )
            .clickable(onClick = onToggle)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        val imageRequest = remember(item.uri, item.isVideo) {
            ImageRequest.Builder(context)
                .data(item.uri)
                .apply {
                    if (item.isVideo) {
                        decoderFactory(VideoFrameDecoder.Factory())
                        videoFrameMillis(1000)
                    }
                }
                .crossfade(true)
                .build()
        }

        AsyncImage(
            model = imageRequest,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Video Indicator Badge
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "VIDEO",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Selection Checkmark Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = MaterialSymbols.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// Android MediaStore Query for Real Recent Images & Videos
private fun loadRecentMedia(context: Context): List<RecentMediaItem> {
    val items = mutableListOf<RecentMediaItem>()

    // 1. Query Images
    try {
        val imgProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )
        val imgSort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imgProjection,
            null,
            null,
            imgSort
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            var count = 0
            while (cursor.moveToNext() && count < 80) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Image"
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                items.add(RecentMediaItem(uri = uri, name = name, size = size, dateAdded = dateAdded, isVideo = false))
                count++
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Operation failed")
    }

    // 2. Query Videos
    try {
        val vidProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )
        val vidSort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            vidProjection,
            null,
            null,
            vidSort
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            var count = 0
            while (cursor.moveToNext() && count < 40) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Video"
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                items.add(RecentMediaItem(uri = uri, name = name, size = size, dateAdded = dateAdded, isVideo = true))
                count++
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Operation failed")
    }

    return items.sortedByDescending { it.dateAdded }
}

// Android MediaStore Query for Real Audio Tracks & Music
private fun loadRecentAudio(context: Context): List<RecentAudioItem> {
    val items = mutableListOf<RecentAudioItem>()
    try {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            var count = 0
            while (cursor.moveToNext() && count < 100) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Audio"
                val title = cursor.getString(titleCol) ?: name
                val rawArtist = cursor.getString(artistCol)
                val artist = if (rawArtist.isNullOrEmpty() || rawArtist == "<unknown>") "Unknown Artist" else rawArtist
                val album = cursor.getString(albumCol) ?: ""
                val durationMs = cursor.getLong(durationCol)
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol)
                val albumId = cursor.getLong(albumIdCol)

                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val albumArtUri = if (albumId > 0) {
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                } else null

                items.add(
                    RecentAudioItem(
                        uri = uri,
                        name = name,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        size = size,
                        dateAdded = dateAdded,
                        albumArtUri = albumArtUri
                    )
                )
                count++
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Audio track query failed")
    }
    return items
}

// Android MediaStore Query for Real Recent Files & Documents
private fun loadRecentFiles(context: Context): List<RecentFileItem> {
    val items = mutableListOf<RecentFileItem>()
    try {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE} AND ${MediaStore.Files.FileColumns.SIZE} > 0"
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

            var count = 0
            while (cursor.moveToNext() && count < 100) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Document"
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol)
                val mimeType = cursor.getString(mimeCol) ?: ""
                val extension = name.substringAfterLast('.', "").uppercase()
                val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)

                items.add(
                    RecentFileItem(
                        uri = uri,
                        name = name,
                        size = size,
                        dateAdded = dateAdded,
                        mimeType = mimeType,
                        extension = extension
                    )
                )
                count++
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Query Downloads directory as well (Android 10+)
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val downloadUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val downloadProjection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_ADDED,
                MediaStore.Downloads.MIME_TYPE
            )
            context.contentResolver.query(
                downloadUri,
                downloadProjection,
                null,
                null,
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)

                var count = 0
                while (cursor.moveToNext() && count < 50) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "File"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val mimeType = cursor.getString(mimeCol) ?: ""
                    val extension = name.substringAfterLast('.', "").uppercase()
                    val uri = ContentUris.withAppendedId(downloadUri, id)

                    if (items.none { it.uri == uri || it.name == name }) {
                        items.add(
                            RecentFileItem(
                                uri = uri,
                                name = name,
                                size = size,
                                dateAdded = dateAdded,
                                mimeType = mimeType,
                                extension = extension
                            )
                        )
                        count++
                    }
                }
            }
        }
    } catch (_: Exception) {}

    return items.sortedByDescending { it.dateAdded }
}

private fun formatDate(dateAddedSeconds: Long): String {
    if (dateAddedSeconds <= 0L) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(dateAddedSeconds * 1000L))
    } catch (_: Exception) {
        ""
    }
}
