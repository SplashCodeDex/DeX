package com.dexstudios.dex.window.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.mirror.toImageBitmap
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import java.util.Base64

/**
 * 100x115dp File & Folder Grid Card with hover lift, press sink, and micro-thumbnail decoding.
 */
@Composable
internal fun FileGridItemCard(
    item: ExplorerFileItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1.0f,
        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
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
            .size(width = 100.dp, height = 115.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected && isHovered -> androidx.compose.ui.graphics.Color(0xFF3D3647)
                    isSelected -> androidx.compose.ui.graphics.Color(0xFF332D3B)
                    isHovered -> MaterialTheme.colorScheme.surfaceVariant
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
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
                val isMedia = item.name.endsWith(".jpg", true) || item.name.endsWith(".png", true) || item.name.endsWith(".jpeg", true)
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isMedia && !item.isDirectory) {
                    val coilUri = if (item.path.startsWith("C:", ignoreCase = true) || item.path.startsWith("W:", ignoreCase = true) || item.path.contains(":\\")) {
                        "file:///" + item.path.replace('\\', '/')
                    } else {
                        item.path
                    }
                    coil3.compose.SubcomposeAsyncImage(
                        model = coilUri,
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
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
        }
    }
}
