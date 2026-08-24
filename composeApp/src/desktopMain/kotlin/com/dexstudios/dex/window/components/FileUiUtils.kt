package com.dexstudios.dex.window.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_article
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_inventory
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_photo
import org.jetbrains.compose.resources.painterResource
import java.awt.Desktop
import java.io.File

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

@Composable
internal fun getFileIcon(item: ExplorerFileItem): androidx.compose.ui.graphics.painter.Painter {
    if (item.isAddFolderButton) return painterResource(Res.drawable.ic_fluent_folder)
    if (item.isDirectory) return painterResource(Res.drawable.ic_fluent_folder)
    val ext = item.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> painterResource(Res.drawable.ic_fluent_photo)
        "zip", "rar", "7z", "tar", "gz" -> painterResource(Res.drawable.ic_fluent_inventory)
        else -> painterResource(Res.drawable.ic_fluent_article)
    }
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

fun getDeXDownloadDirectory(): String {
    val userHome = System.getProperty("user.home") ?: ""
    val dir = File(userHome, "Downloads/DeX")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir.absolutePath
}
