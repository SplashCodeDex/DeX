package com.dexstudios.dex.window.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_article
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_inventory
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_photo
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format("%.1f %s", value, units[digitGroups])
}

internal fun formatSpeed(bps: Long): String {
    val mbps = bps / (1024.0 * 1024.0)
    return String.format("%.1f MB/s", mbps)
}

internal fun String.truncateMiddle(maxLength: Int): String {
    if (this.length <= maxLength) return this
    if (maxLength <= 5) return this.take(maxLength)

    val half = (maxLength - 3) / 2
    val start = this.take(half + if ((maxLength - 3) % 2 != 0) 1 else 0)
    val end = this.takeLast(half)
    return "$start...$end"
}

internal fun File.isImage(): Boolean {
    val ext = this.extension.lowercase()
    return ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
}

internal fun File.isVideo(): Boolean {
    val ext = this.extension.lowercase()
    return ext in listOf("mp4", "mkv", "avi", "mov", "webm", "wmv", "flv")
}

/**
 * Neutral code-drawn placeholder shown while an icon is not yet in the session cache -
 * the only state a grid card can ever observe, because icons are decoded exclusively
 * during startup warmup (see [FileIconLibraryWarmup]) and never again at render time.
 */
private object FallbackIconPainter : Painter() {
    override val intrinsicSize = Size(24f, 24f)
    override fun DrawScope.onDraw() {
        drawCircle(color = Color.Gray.copy(alpha = 0.35f), radius = size.minDimension / 2f)
    }
}

// Session-wide icon cache: four painters decoded once, read for the app's lifetime.
private val iconCache = ConcurrentHashMap<DrawableResource, Painter>()

private val iconLibrary = listOf(
    Res.drawable.ic_fluent_folder,
    Res.drawable.ic_fluent_photo,
    Res.drawable.ic_fluent_inventory,
    Res.drawable.ic_fluent_article,
)

/**
 * Decodes every explorer glyph exactly once, at startup. At t=0 the running process
 * owns freshly-built intact jars, so the continuous-rebuild race that tears ZipFile
 * reads mid-session cannot reach this path - later renders only touch memory.
 * Compose forbids try/catch around composable calls, which is precisely why icon
 * loading lives here and never inside per-item composition.
 */
@Composable
internal fun FileIconLibraryWarmup(modifier: Modifier = Modifier) {
    iconLibrary.forEach { resource ->
        if (!iconCache.containsKey(resource)) {
            iconCache[resource] = painterResource(resource)
        }
    }
    Box(modifier = modifier.size(0.dp))
}

/**
 * Pure cache reader: never touches the jar at render time, so a mid-session rebuild
 * can no longer tear an icon read. Items composed before warmup completes (or after
 * any hypothetical decode failure) render the neutral fallback painter instead.
 */
@Composable
internal fun getFileIcon(item: ExplorerFileItem): Painter {
    val resource = when {
        item.isAddFolderButton || item.isDirectory -> Res.drawable.ic_fluent_folder

        else -> when (item.name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif" -> Res.drawable.ic_fluent_photo
            "zip", "rar", "7z", "tar", "gz" -> Res.drawable.ic_fluent_inventory
            else -> Res.drawable.ic_fluent_article
        }
    }
    return iconCache[resource] ?: FallbackIconPainter
}

internal fun getFileIconColor(item: ExplorerFileItem): Color {
    if (item.isAddFolderButton) return Color(0xFF10B981) // Emerald
    if (item.isDirectory) return Color(0xFFFBBF24) // Amber Folder
    val ext = item.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> Color(0xFF60A5FA)

        // Blue
        "mp4", "mkv", "avi", "mov" -> Color(0xFFF472B6)

        // Pink
        "mp3", "wav", "flac", "m4a" -> Color(0xFFA78BFA)

        // Purple
        "pdf", "doc", "docx", "txt" -> Color(0xFF34D399)

        // Emerald
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFFF59E0B)

        // Amber
        else -> Color(0xFF9CA3AF) // Gray
    }
}

internal fun handleItemDoubleClick(item: ExplorerFileItem, onDrillDown: (String) -> Unit) {
    if (item.isDirectory) {
        onDrillDown(item.path)
    } else {
        // Dangerous file launch protection
        val ext = item.name.substringAfterLast('.', "").lowercase()
        val dangerousExtensions = setOf("exe", "bat", "cmd", "msi", "ps1", "vbs", "jar")
        if (ext in dangerousExtensions) {
            Logger.i("Security block: dangerous executable file prevented from direct double-click launch: ${item.name}")
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

/**
 * Open a local file with the OS default application.
 */
fun openFileNative(filePath: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) return
        val ext = file.extension.lowercase()
        val dangerousExtensions = setOf("exe", "bat", "cmd", "msi", "ps1", "vbs", "jar")
        if (ext in dangerousExtensions) {
            Logger.w("Security block: prevented direct launch of executable: ${file.name}")
            openFolderAndSelectNative(filePath)
            return
        }
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(file)
        }
    } catch (e: Exception) {
        Logger.w("Failed to open file natively: ${e.message}")
    }
}

/**
 * Reveal and select a file inside Windows Explorer or macOS Finder.
 */
fun openFolderAndSelectNative(filePath: String) {
    try {
        val file = File(filePath)
        val absPath = file.absolutePath
        if (com.dexstudios.dex.platform.DesktopEnvironment.isWindows) {
            ProcessBuilder("explorer.exe", "/select,$absPath").start()
        } else if (com.dexstudios.dex.platform.DesktopEnvironment.isMacOS) {
            ProcessBuilder("open", "-R", absPath).start()
        } else {
            val parent = file.parentFile ?: file
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(parent)
            }
        }
    } catch (e: Exception) {
        Logger.w("Failed to open folder natively: ${e.message}")
    }
}

fun getDeXDownloadDirectory(): String = com.dexstudios.dex.core.network.server.ReceiveStorage.downloadsDir().absolutePath

/**
 * Copy plain text (such as file or folder path) to the system clipboard.
 */
fun copyTextToClipboard(text: String) {
    try {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    } catch (e: Exception) {
        Logger.w("Failed to copy text to clipboard: ${e.message}")
    }
}

/**
 * AWT Transferable wrapper for a list of physical files (DataFlavor.javaFileListFlavor),
 * supporting native Drag-and-Drop and clipboard transfers to OS Explorer / Finder / desktop apps.
 */
class FileListTransferable(val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.javaFileListFlavor || flavor == DataFlavor.stringFlavor

    override fun getTransferData(flavor: DataFlavor?): Any = when (flavor) {
        DataFlavor.javaFileListFlavor -> files
        DataFlavor.stringFlavor -> files.joinToString("\n") { it.absolutePath }
        else -> throw UnsupportedFlavorException(flavor)
    }
}

/**
 * Copy a physical file to the system clipboard as a file-list payload (DataFlavor.javaFileListFlavor),
 * enabling direct Ctrl+V / Cmd+V paste into OS directories or desktop apps.
 */
fun copyFileToClipboard(filePath: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) return
        val transferable = FileListTransferable(listOf(file))
        Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
    } catch (e: Exception) {
        Logger.w("Failed to copy file to clipboard: ${e.message}")
    }
}

// --- Local micro-thumbnails for History mode -------------------------------------------
// Phone-side listings arrive with pre-encoded thumbBase64; local PC files have no such
// producer, so History mode generates its own 96px JPEG micro-thumbs, keyed by path and
// mtime so unchanged files decode exactly once per session.

private const val ThumbEdgePx = 96
private const val MaxThumbSourceBytes = 25L * 1024 * 1024
private const val MaxCachedThumbs = 1024

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
private val thumbnailCache = ConcurrentHashMap<String, String?>()

internal fun isLocalImageFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in imageExtensions

/** Returns a Base64 JPEG micro-thumbnail, or null when the file is not a decodable image. */
internal fun localFileThumbBase64(path: String?, lastModified: Long): String? {
    if (path.isNullOrBlank()) return null
    if (!isLocalImageFile(path)) return null

    val cacheKey = "$path|$lastModified"
    if (thumbnailCache.containsKey(cacheKey)) return thumbnailCache[cacheKey]

    val encoded = runCatching {
        val f = File(path)
        if (!f.isFile || f.length() > MaxThumbSourceBytes) return@runCatching null
        val source = ImageIO.read(f) ?: return@runCatching null
        val scale = minOf(1f, ThumbEdgePx.toFloat() / maxOf(source.width, source.height))
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        g.drawImage(source, 0, 0, w, h, null)
        g.dispose()
        val bytes = ByteArrayOutputStream()
        ImageIO.write(scaled, "jpg", bytes)
        Base64.getEncoder().encodeToString(bytes.toByteArray())
    }.getOrNull()

    if (thumbnailCache.size > MaxCachedThumbs) thumbnailCache.clear()
    thumbnailCache[cacheKey] = encoded
    return encoded
}
