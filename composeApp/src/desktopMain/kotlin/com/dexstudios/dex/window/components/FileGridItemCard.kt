package com.dexstudios.dex.window.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.mirror.toImageBitmap
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * 100x115dp File & Folder Grid Card with hover lift, press sink, and micro-thumbnail decoding.
 *
 * Selection paints with the theme's primaryContainer so it stays legible in both themes;
 * hover over a selected card layers a subtle onSurface wash instead of a second hardcoded tone.
 */
@Composable
internal fun FileGridItemCard(item: ExplorerFileItem, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1.0f,
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
            .height(115.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    isHovered -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                },
            )
            .background(
                if (isSelected && isHovered) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            // 48x48dp Icon or Micro-Thumbnail Glyph
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                        modifier = Modifier.size(28.dp),
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
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )

            // File Size or Timestamp Sub-label
            Text(
                text = if (item.isDirectory) (if (item.isAddFolderButton) "SAF Picker" else "Folder") else formatFileSize(item.size),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
    }
}
