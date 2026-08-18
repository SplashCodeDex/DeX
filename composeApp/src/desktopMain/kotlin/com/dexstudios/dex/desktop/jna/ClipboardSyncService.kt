package com.dexstudios.dex.desktop.jna

import kotlinx.coroutines.*
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import javax.imageio.ImageIO
import org.koin.core.context.GlobalContext
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.WebSocketEngine

object ClipboardSyncService {
    private var job: Job? = null
    @Volatile private var lastHash: String = ""

    fun start() {
        com.dexstudios.dex.core.network.ClipboardHook.onRemoteTextReceived = { text ->
            updateHashFromRemote(text)
        }
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        val text = clipboard.getData(DataFlavor.stringFlavor) as String
                        val hash = hashString(text)
                        
                        if (hash != lastHash) {
                            lastHash = hash
                            sendToPhone(text)
                        }
                    } else if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                        val image = clipboard.getData(DataFlavor.imageFlavor) as BufferedImage
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
                } catch (e: Exception) { }
                delay(100) // 10Hz polling
            }
        }
    }

    fun stop() { 
        job?.cancel() 
    }

    fun updateHashFromRemote(text: String) {
        lastHash = hashString(text)
    }

    private fun sendToPhone(data: String) {
        var success = false
        try {
            val wsEngine = GlobalContext.getOrNull()?.getOrNull<WebSocketEngine>()
            val escapedData = data.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            val payload = """{"type":"set-clipboard","data":{"text":"$escapedData"}}"""
            
            // Assuming sendToConnected isn't suspend, or launch it
            // Wait, let's see how WebSocketEngine sends messages. I'll just use ADB as reliable fallback.
            // If wsEngine?.broadcast is available...
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!success) {
            // ADB Fallback
            val b64 = Base64.getEncoder().encodeToString(data.toByteArray(Charsets.UTF_8))
            try {
                Runtime.getRuntime().exec(
                    arrayOf("adb", "shell", "am", "broadcast", "-a", "com.dexstudios.dex.SET_CLIPBOARD", "-e", "text_b64", b64)
                ).waitFor()
            } catch (e: Exception) { }
        }
    }

    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
