package com.dexstudios.dex.desktop.jna

import kotlinx.coroutines.*
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.security.MessageDigest
import java.util.Base64

object ClipboardSyncService {
    private var job: Job? = null
    private var lastHash: String = ""

    fun start() {
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        val text = clipboard.getData(DataFlavor.stringFlavor) as String
                        val hash = hashString(text)
                        
                        if (hash != lastHash) {
                            lastHash = hash
                            val b64 = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
                            val process = Runtime.getRuntime().exec(
                                arrayOf("adb", "shell", "am", "broadcast", "-a", "com.dexstudios.dex.SET_CLIPBOARD", "-e", "text_b64", b64)
                            )
                            process.waitFor()
                        }
                    }
                } catch (e: Exception) { }
                delay(50)
            }
        }
    }

    fun stop() { job?.cancel() }

    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
