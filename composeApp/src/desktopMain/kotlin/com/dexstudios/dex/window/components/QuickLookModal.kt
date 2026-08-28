package com.dexstudios.dex.window.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.frostedSurface
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_arrow_back
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_article
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_close
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_inventory
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_photo
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.mirror.toImageBitmap
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Fluid Quick Look modal preview dialog for Desktop DeX.
 * Triggered via Spacebar on any selected item; all keyboard interaction (Space/Escape,
 * arrows, Enter) is owned by FileExplorerPanel's root onPreviewKeyEvent so that focus
 * stays on the panel — this composable must never claim focus or handle keys itself.
 * Supports image viewing, text/code inspection, metadata breakdown, and keyboard navigation.
 */
@Composable
fun QuickLookModal(
    item: ExplorerFileItem,
    currentIndex: Int,
    totalCount: Int,
    isPhoneConnected: Boolean = false,
    onDismiss: () -> Unit,
    onOpenNative: () -> Unit,
    onOpenLocation: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSendToPhone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val file = remember(item.path) { File(item.path) }
    val ext = remember(item.name) { item.name.substringAfterLast('.', "").lowercase() }

    val isImage = remember(ext) { ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif") }
    val isText = remember(ext) { ext in listOf("txt", "md", "kt", "java", "py", "json", "xml", "log", "csv", "yaml", "yml", "ini", "properties", "gradle", "bat", "sh") }

    // Dim Backdrop + Centered Card
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(min = 460.dp, max = 560.dp)
                .heightIn(min = 340.dp, max = 460.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent backdrop click from dismissing */ },
                )
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = Color.Black.copy(alpha = 0.6f),
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                )
                .frostedSurface(
                    shape = RoundedCornerShape(24.dp),
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    opacity = 0.98f,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(24.dp),
                )
                .clip(RoundedCornerShape(24.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                // === Header: Item Name, Action Button & Close ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val iconResource = when {
                        item.isDirectory -> Res.drawable.ic_fluent_folder
                        isImage -> Res.drawable.ic_fluent_photo
                        isText -> Res.drawable.ic_fluent_article
                        else -> Res.drawable.ic_fluent_inventory
                    }

                    Icon(
                        painter = painterResource(iconResource),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${formatFileSize(item.size)} • ${formatTimestamp(item.timestamp)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // "Open with App" Primary Action Button
                    val openInteraction = remember { MutableInteractionSource() }
                    val isOpenHovered by openInteraction.collectIsHoveredAsState()
                    val openScale by animateFloatAsState(
                        targetValue = if (isOpenHovered) 1.05f else 1.0f,
                        animationSpec = tween(300, easing = DockCardPhysics.HoverEase),
                        label = "openScale",
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = openScale
                                scaleY = openScale
                            }
                            .bubbleFluidity()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(
                                interactionSource = openInteraction,
                                indication = null,
                                onClick = onOpenNative,
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (item.isDirectory) "Open Folder" else "Open App",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_fluent_close),
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // === Middle: Content Preview Pane ===
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isImage && file.exists() -> {
                            ImagePreviewPane(file = file)
                        }

                        isText && file.exists() -> {
                            TextPreviewPane(file = file)
                        }

                        else -> {
                            GenericMetadataPreviewPane(
                                item = item,
                                file = file,
                                ext = ext,
                                onOpenLocation = onOpenLocation,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // === Footer: Navigation Stepper & Hints ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Previous Button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .clickable(onClick = onPrevious),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_fluent_arrow_back),
                                contentDescription = "Previous",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(14.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${currentIndex + 1} of $totalCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Next Button (Arrow back rotated 180)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .clickable(onClick = onNext),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_fluent_arrow_back),
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 180f },
                            )
                        }
                    }

                    Text(
                        text = "Spacebar or Esc to close • ← → to step",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

private sealed interface ImagePreviewState {
    data object Loading : ImagePreviewState
    data class Success(val bitmap: ImageBitmap) : ImagePreviewState
    data object Error : ImagePreviewState
}

@Composable
private fun ImagePreviewPane(file: File) {
    val state by produceState<ImagePreviewState>(initialValue = ImagePreviewState.Loading, key1 = file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            val result = runCatching {
                val buffered = ImageIO.read(file)
                buffered?.toComposeImageBitmap()
            }.getOrNull()

            if (result != null) {
                ImagePreviewState.Success(result)
            } else {
                ImagePreviewState.Error
            }
        }
    }

    when (val s = state) {
        is ImagePreviewState.Loading -> {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        is ImagePreviewState.Success -> {
            Image(
                bitmap = s.bitmap,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }

        is ImagePreviewState.Error -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(Res.drawable.ic_fluent_photo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Unable to decode image preview",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TextPreviewPane(file: File) {
    val textLinesState by produceState<List<String>?>(initialValue = null, key1 = file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                file.useLines { lines -> lines.take(120).toList() }
            }.getOrNull()
        }
    }

    val lines = textLinesState
    if (lines == null) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    } else if (lines.isEmpty()) {
        Text(
            text = "File is empty",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .verticalScroll(scrollState),
        ) {
            lines.forEachIndexed { index, line ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${index + 1}".padStart(3, ' '),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.width(32.dp),
                    )
                    Text(
                        text = line,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenericMetadataPreviewPane(item: ExplorerFileItem, file: File, ext: String, onOpenLocation: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (item.isDirectory) Res.drawable.ic_fluent_folder else Res.drawable.ic_fluent_inventory,
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (ext.isNotBlank()) {
                "${ext.uppercase()} File"
            } else if (item.isDirectory) {
                "Folder"
            } else {
                "Binary File"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.path,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Open Location Button
        Box(
            modifier = Modifier
                .bubbleFluidity()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpenLocation)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Show in File Location",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0) return "Unknown date"
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
