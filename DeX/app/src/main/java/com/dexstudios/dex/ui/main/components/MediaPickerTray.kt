package com.dexstudios.dex.ui.main.components

import android.content.ContentUris
import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dexstudios.dex.ui.components.LiquidGlassButton
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassTokens
import com.dexstudios.dex.ui.components.glass.shinyGlare
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecentMediaItem(
    val uri: Uri,
    val name: String,
    val size: Long = 0L,
    val isVideo: Boolean = false,
)

@Composable
fun MediaPickerTray(
    backdrop: Backdrop?,
    onSend: (List<Uri>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val selectedUris = remember { mutableStateListOf<Uri>() }
    var mediaItems by remember { mutableStateOf<List<RecentMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 1. Storage Document Picker for 'Documents'
    val systemDocPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { if (!selectedUris.contains(it)) selectedUris.add(it) }
        }
    }

    // 2. Audio Picker for 'Audio'
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { if (!selectedUris.contains(it)) selectedUris.add(it) }
        }
    }

    // 3. Camera capture launcher for 'Camera'
    val cameraCapture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            // Save temporary captured bitmap to cache and add to selected URIs
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
                e.printStackTrace()
            }
        }
    }

    // Load recent media files from MediaStore
    LaunchedEffect(Unit) {
        isLoading = true
        mediaItems = withContext(Dispatchers.IO) {
            loadRecentMedia(context)
        }
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        // --- 1. Top Quick Action Buttons: 'Documents', 'Camera', 'Audio' ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickActionButton(
                label = "Documents",
                icon = MaterialSymbols.Folder,
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                onClick = { systemDocPicker.launch(arrayOf("*/*")) }
            )

            QuickActionButton(
                label = "Camera",
                icon = MaterialSymbols.Photo,
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                onClick = { cameraCapture.launch(null) }
            )

            QuickActionButton(
                label = "Audio",
                icon = MaterialSymbols.Article,
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                onClick = { audioPicker.launch("audio/*") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. Recent Media Grid ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (mediaItems.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No recent media found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(mediaItems, key = { it.uri.toString() }) { item ->
                        val isItemSelected = selectedUris.contains(item.uri)

                        MediaThumbnailCard(
                            item = item,
                            isSelected = isItemSelected,
                            onToggle = {
                                if (isItemSelected) selectedUris.remove(item.uri)
                                else selectedUris.add(item.uri)
                            }
                        )
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
 * Clean quick action button for top categories.
 */
@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    backdrop: Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonShape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .height(44.dp)
            .bubbleFluidity(targetScale = 1.04f, pullFactor = 0.05f)
            .clip(buttonShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (backdrop != null) {
            LiquidGlassPanel(
                backdrop = backdrop,
                modifier = Modifier
                    .matchParentSize()
                    .shinyGlare(shape = buttonShape, intensity = LiquidGlassTokens.GlareFactor * 0.7f),
                shape = buttonShape,
                config = LiquidGlassPresets.IconButton.copy(
                    shape = buttonShape,
                    blurRadius = 10.dp,
                    surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                    surfaceTintAlpha = 0.22f,
                ),
                content = {}
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Card for each media item in the grid.
 */
@Composable
private fun MediaThumbnailCard(
    item: RecentMediaItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemShape = RoundedCornerShape(14.dp)

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
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
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

// MediaStore Query Helper for Recent Images & Videos
private fun loadRecentMedia(context: Context): List<RecentMediaItem> {
    val items = mutableListOf<RecentMediaItem>()

    // Query Images
    try {
        val imgProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE)
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
            var count = 0
            while (cursor.moveToNext() && count < 60) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "image"
                val size = cursor.getLong(sizeCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                items.add(RecentMediaItem(uri = uri, name = name, size = size, isVideo = false))
                count++
            }
        }
    } catch (_: Exception) {}

    // Query Videos
    try {
        val vidProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE)
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
            var count = 0
            while (cursor.moveToNext() && count < 30) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "video"
                val size = cursor.getLong(sizeCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                items.add(RecentMediaItem(uri = uri, name = name, size = size, isVideo = true))
                count++
            }
        }
    } catch (_: Exception) {}

    return items
}
