package com.dexstudios.dex.core.network.services

import co.touchlab.kermit.Logger
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

data class WallpaperPayload(val bytes: ByteArray, val contentType: String, val etag: String)

object DesktopWallpaperService {
    @Volatile
    private var cachedWallpaper: ByteArray? = null

    @Volatile
    private var cachedContentType: String = "image/jpeg"

    @Volatile
    private var cachedETag: String = ""

    @Volatile
    private var lastFetchTime: Long = 0L

    private val lock = Any()

    fun invalidateCache() {
        synchronized(lock) {
            cachedWallpaper = null
            lastFetchTime = 0L
            cachedETag = ""
        }
    }

    fun getWallpaper480p(): WallpaperPayload? {
        val now = System.currentTimeMillis()
        val cachedBytes = cachedWallpaper
        val etag = cachedETag
        if (cachedBytes != null && now - lastFetchTime < 5_000L) {
            return WallpaperPayload(cachedBytes, cachedContentType, etag)
        }

        synchronized(lock) {
            if (cachedWallpaper != null && now - lastFetchTime < 5_000L) {
                return WallpaperPayload(cachedWallpaper!!, cachedContentType, cachedETag)
            }

            val (rawFile, rawBytes, contentType) = tryReadRawWallpaper() ?: return null
            val lastWriteTicks = rawFile?.lastModified() ?: now
            val fileSize = rawFile?.length() ?: rawBytes.size.toLong()
            val computedEtag = "\"W/$lastWriteTicks-$fileSize\""

            return try {
                val resized = resizeTo480p(rawBytes)
                cachedWallpaper = resized
                cachedContentType = "image/jpeg"
                cachedETag = computedEtag
                lastFetchTime = now
                WallpaperPayload(resized, "image/jpeg", computedEtag)
            } catch (e: Exception) {
                Logger.i("DesktopWallpaperService: Downscaling fallback: ${e.message}")
                cachedWallpaper = rawBytes
                cachedContentType = contentType
                cachedETag = computedEtag
                lastFetchTime = now
                WallpaperPayload(rawBytes, contentType, computedEtag)
            }
        }
    }

    private data class RawWallpaperResult(val file: File?, val bytes: ByteArray, val contentType: String)

    private fun tryReadRawWallpaper(): RawWallpaperResult? {
        val osName = System.getProperty("os.name", "")
        val isWindows = osName.contains("Windows", ignoreCase = true)
        val isMac = osName.contains("Mac", ignoreCase = true)

        if (isWindows) {
            val appData = System.getenv("APPDATA") ?: ""
            val themesDir = File(appData, "Microsoft/Windows/Themes")
            val candidateFiles = listOf(
                File(themesDir, "TranscodedWallpaper"),
                File(themesDir, "TranscodedWallpaper_000"),
                File(themesDir, "TranscodedWallpaper_001"),
            )

            for (candidate in candidateFiles) {
                if (candidate.exists() && candidate.isFile && candidate.length() > 0) {
                    val bytes = runCatching { candidate.readBytes() }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val isJpeg = bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
                        val contentType = if (isJpeg) "image/jpeg" else "image/png"
                        return RawWallpaperResult(candidate, bytes, contentType)
                    }
                }
            }

            // Fallback: Registry HKCU\Control Panel\Desktop\Wallpaper via reg query
            runCatching {
                val process = ProcessBuilder("reg", "query", "HKCU\\Control Panel\\Desktop", "/v", "Wallpaper")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                val match = Regex("Wallpaper\\s+REG_SZ\\s+(.*)").find(output)
                val path = match?.groupValues?.get(1)?.trim()
                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    if (file.exists() && file.isFile && file.length() > 0) {
                        val bytes = file.readBytes()
                        if (bytes.isNotEmpty()) {
                            return RawWallpaperResult(file, bytes, "image/jpeg")
                        }
                    }
                }
            }
        } else if (isMac) {
            // macOS wallpaper path lookup via AppleScript
            runCatching {
                val process = ProcessBuilder("osascript", "-e", "tell app \"finder\" to get posix path of (get desktop picture as alias)")
                    .redirectErrorStream(true)
                    .start()
                val path = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                if (path.isNotBlank()) {
                    val file = File(path)
                    if (file.exists() && file.isFile && file.length() > 0) {
                        val bytes = file.readBytes()
                        if (bytes.isNotEmpty()) {
                            return RawWallpaperResult(file, bytes, "image/jpeg")
                        }
                    }
                }
            }
        }

        return null
    }

    private fun resizeTo480p(rawBytes: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(rawBytes)
        val original = ImageIO.read(bis) ?: throw IllegalArgumentException("Unreadable image stream")

        val targetHeight = 480
        val targetWidth = if (original.height > 0) (original.width * targetHeight) / original.height else 854

        val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = resized.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null)
        g2d.dispose()

        val baos = ByteArrayOutputStream()
        ImageIO.write(resized, "jpg", baos)
        return baos.toByteArray()
    }
}
