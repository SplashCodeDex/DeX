package com.dexstudios.dex.core.network

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import timber.log.Timber

/**
 * Captures the phone screen via MediaProjection and streams JPEG frames to the
 * connected PC over the WebSocket (view-only mirror, no ADB required).
 *
 * The PC initiates with a `mirror-start` message; [requestStart] surfaces the
 * consent request to the UI ([pendingConsent]), and after the user grants it,
 * MainActivity calls [start] with the projection. Frames are sent as binary
 * WebSocket frames at [TARGET_FPS] fps.
 */
object MirrorSession {
    const val TARGET_WIDTH = 720
    const val TARGET_FPS = 15
    const val JPEG_QUALITY = 70

    @Volatile
    var active = false
        private set

    // Per-frame allocations cached to avoid ~4MB of bitmap churn per 15fps frame
    private var cachedBitmap: Bitmap? = null
    private var cachedBitmapWidth = 0
    private var cachedBitmapHeight = 0
    private val jpegOut = java.io.ByteArrayOutputStream(65536)

    @Volatile
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var frameJob: Job? = null

    /** Sends a text message to the connected PC (set by WebSocketEngine). */
    var textSender: ((String) -> Unit)? = null

    /** Sends a binary frame to the connected PC (set by WebSocketEngine). */
    var frameSender: ((ByteArray) -> Unit)? = null

    /** Callback to update foreground service type (set by DexService). */
    var onMirroringStateChanged: ((Boolean) -> Unit)? = null

    /** Set to true when the PC asks to start mirroring; observed by MainActivity. */
    val pendingConsent = MutableStateFlow(false)

    /** The PC asked to mirror; surface the consent dialog to the user. */
    fun requestStart() {
        if (active) return
        pendingConsent.value = true
    }

    /**
     * Starts streaming with the user-granted projection.
     * @param width Screen width in pixels (unscaled).
     * @param height Screen height in pixels (unscaled).
     * @param densityDpi Display density.
     */
    fun start(projection: MediaProjection, width: Int, height: Int, densityDpi: Int) {
        stop()
        active = true
        pendingConsent.value = false
        this.projection = projection
        onMirroringStateChanged?.invoke(true)

        // Scale to a streaming-friendly resolution (720px wide, aspect preserved)
        val scale = TARGET_WIDTH.toFloat() / width
        val streamWidth = TARGET_WIDTH
        val streamHeight = (height * scale).toInt().coerceAtLeast(480)

        val reader = ImageReader.newInstance(streamWidth, streamHeight, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        val vd = projection.createVirtualDisplay(
            "DeXMirror",
            streamWidth, streamHeight, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )
        virtualDisplay = vd

        textSender?.invoke("""{"type":"mirror-config","data":{"width":$streamWidth,"height":$streamHeight,"fps":$TARGET_FPS}}""")
        Timber.i("Mirror started: ${streamWidth}x$streamHeight @ ${TARGET_FPS}fps")

        frameJob = CoroutineScope(Dispatchers.Default).launch {
            val frameIntervalMs = 1000L / TARGET_FPS
            while (isActive && active) {
                reader.acquireLatestImage()?.use { image ->
                    try {
                        val jpeg = imageToJpeg(image, streamHeight)
                        if (jpeg != null) frameSender?.invoke(jpeg)
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing frame")
                    }
                }
                delay(frameIntervalMs.milliseconds)
            }
        }
    }

    /** Stops streaming and releases all capture resources. */
    fun stop() {
        active = false
        pendingConsent.value = false
        frameJob?.cancel()
        frameJob = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        cachedBitmap?.recycle()
        cachedBitmap = null
        cachedBitmapWidth = 0
        cachedBitmapHeight = 0
        projection?.stop()
        projection = null
        onMirroringStateChanged?.invoke(false)
        Timber.i("Mirror stopped")
    }

    /** The user denied the screen-capture consent: tell the PC to close its mirror window. */
    fun deny() {
        textSender?.invoke("""{"type":"mirror-denied","data":{}}""")
        stop()
    }

    private fun imageToJpeg(image: android.media.Image, height: Int): ByteArray? {
        val width = TARGET_WIDTH
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            // Reuse the full-width bitmap across frames — only re-allocate when
            // dimensions change. Cuts per-frame allocs from ~4MB to near zero.
            val bmpWidth = width + rowPadding / pixelStride
            if (cachedBitmap == null || cachedBitmapWidth != bmpWidth || cachedBitmapHeight != height) {
                cachedBitmap?.recycle()
                cachedBitmap = createBitmap(bmpWidth, height)
                cachedBitmapWidth = bmpWidth
                cachedBitmapHeight = height
            }
            cachedBitmap?.copyPixelsFromBuffer(buffer)

            val cropped = if (rowPadding > 0) {
                Bitmap.createBitmap(cachedBitmap!!, 0, 0, width, height)
            } else {
                cachedBitmap!! // safe: we just assigned it above if null
            }
            jpegOut.reset()
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpegOut)
            if (rowPadding > 0) cropped.recycle()
            // cachedBitmap stays around for the next frame — do not recycle
            return jpegOut.toByteArray()
        } catch (e: Exception) {
            Timber.e(e, "JPEG encode failed")
            return null
        }
    }
}

