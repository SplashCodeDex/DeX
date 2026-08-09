package com.dexstudios.dex.network

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var active = false

    @Volatile
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var frameJob: Job? = null

    /** Sends a text message to the connected PC (set by WebSocketClientService). */
    var textSender: ((String) -> Unit)? = null

    /** Sends a binary frame to the connected PC (set by WebSocketClientService). */
    var frameSender: ((ByteArray) -> Unit)? = null

    /** Set to true when the PC asks to start mirroring; observed by MainActivity. */
    val pendingConsent = MutableStateFlow(false)

    val isActive: Boolean get() = active

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
        DexService.setMirroring(true)

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
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val jpeg = imageToJpeg(image, streamWidth, streamHeight)
                        if (jpeg != null) frameSender?.invoke(jpeg)
                    } finally {
                        image.close()
                    }
                }
                delay(frameIntervalMs)
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
        projection?.stop()
        projection = null
        DexService.setMirroring(false)
        Timber.i("Mirror stopped")
    }

    /** The user denied the screen-capture consent: tell the PC to close its mirror window. */
    fun deny() {
        textSender?.invoke("""{"type":"mirror-denied","data":{}}""")
        stop()
    }

    private fun imageToJpeg(image: android.media.Image, width: Int, height: Int): ByteArray? {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            // The buffer may be padded; create a wider bitmap and crop afterwards
            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val cropped = if (rowPadding > 0) Bitmap.createBitmap(bitmap, 0, 0, width, height) else bitmap
            val out = java.io.ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
            return out.toByteArray()
        } catch (e: Exception) {
            Timber.e(e, "JPEG encode failed")
            return null
        }
    }
}
