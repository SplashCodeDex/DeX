package com.dexstudios.dex.desktop.jna

import com.dexstudios.dex.core.network.DiscoveryEngine
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.koin.core.context.GlobalContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.FlavorListener
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import javax.imageio.ImageIO

object ClipboardSyncService {
    private var processJob: Job? = null

    @Volatile private var lastHash: String = ""

    private val flavorListener = FlavorListener {
        // Debounce rapid bursts (e.g. Excel copying 15 formats at once)
        processJob?.cancel()
        processJob = CoroutineScope(Dispatchers.IO).launch {
            delay(300)
            processClipboard()
        }
    }

    private var deviceConfig: com.dexstudios.dex.core.network.DeviceConfig? = null

    fun start(config: com.dexstudios.dex.core.network.DeviceConfig) {
        this.deviceConfig = config
        com.dexstudios.dex.core.network.ClipboardHook.onRemoteTextReceived = { text ->
            updateHashFromRemote(text)
        }
        try {
            Toolkit.getDefaultToolkit().systemClipboard.addFlavorListener(flavorListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        processJob?.cancel()
        try {
            Toolkit.getDefaultToolkit().systemClipboard.removeFlavorListener(flavorListener)
        } catch (e: Exception) { }
    }

    private fun processClipboard() {
        if (deviceConfig?.clipboardSyncEnabled != true) return

        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                val text = clipboard.getData(DataFlavor.stringFlavor) as? String ?: return
                val hash = hashString(text)

                if (hash != lastHash) {
                    lastHash = hash
                    sendToPhone(text)
                }
            } else if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                val image = clipboard.getData(DataFlavor.imageFlavor) as? BufferedImage ?: return
                val baos = ByteArrayOutputStream()
                ImageIO.write(image, "png", baos)
                val imageBytes = baos.toByteArray()
                val b64Image = Base64.getEncoder().encodeToString(imageBytes)
                val hash = hashString(b64Image)

                if (hash != lastHash) {
                    lastHash = hash
                    // Send image base64 JSON
                    val jsonPayload = """{"type":"image", "mime":"image/png", "imageBase64":"$b64Image"}"""
                    sendToPhone(jsonPayload)
                }
            }
        } catch (e: java.lang.IllegalStateException) {
            // Clipboard is locked by another process (common on Windows).
            // We can safely ignore as the user hasn't successfully copied it yet.
            println("ClipboardSyncService: Clipboard locked (${e.message})")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateHashFromRemote(text: String) {
        lastHash = hashString(text)
    }

    private fun sendToPhone(data: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = if (data.startsWith("{") && data.endsWith("}")) {
                    data
                } else {
                    buildJsonObject {
                        put("type", "set-clipboard")
                        putJsonObject("data") {
                            put("text", data)
                        }
                    }.toString()
                }

                val success = com.dexstudios.dex.core.network.server.WebSocketConnectionManager.broadcastToPaired(payload)

                if (!success) {
                    // ADB Fallback
                    val b64 = Base64.getEncoder().encodeToString(data.toByteArray(Charsets.UTF_8))
                    try {
                        Runtime.getRuntime().exec(
                            arrayOf("adb", "shell", "am", "broadcast", "-a", "com.dexstudios.dex.SET_CLIPBOARD", "-e", "text_b64", b64),
                        ).waitFor()
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
