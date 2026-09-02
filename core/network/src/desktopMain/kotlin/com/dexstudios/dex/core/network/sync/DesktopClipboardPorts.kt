package com.dexstudios.dex.core.network.sync

import com.dexstudios.dex.core.domain.clipboard.ClipboardAccess
import com.dexstudios.dex.core.domain.clipboard.ClipboardPayload
import com.dexstudios.dex.core.domain.clipboard.ClipboardSender
import com.dexstudios.dex.core.network.ClipboardSyncState
import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.core.protocol.MessageTypes
import com.dexstudios.dex.core.protocol.ProtocolEnvelope
import kotlinx.serialization.json.put
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Desktop port implementations for the domain clipboard use case (plan 029):
 * AWT-backed [ClipboardAccess] and WS-broadcast [ClipboardSender]. Compose-side code
 * (ClipboardSyncService) keeps ONLY the AWT flavor-listener plumbing — every decision
 * (enable gate, echo guard, payload shaping) lives in the use case.
 */
object DesktopClipboardPorts {

    fun awtAccess(): ClipboardAccess = object : ClipboardAccess {
        override suspend fun read(): ClipboardPayload? = runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            when {
                clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor) ->
                    (clipboard.getData(DataFlavor.stringFlavor) as? String)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { ClipboardPayload.Text(it) }

                clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) ->
                    (clipboard.getData(DataFlavor.imageFlavor) as? BufferedImage)?.let { image ->
                        val baos = ByteArrayOutputStream()
                        ImageIO.write(image, "png", baos)
                        ClipboardPayload.Image("image/png", Base64.getEncoder().encodeToString(baos.toByteArray()))
                    }

                else -> null
            }
        }.getOrNull()

        override suspend fun write(payload: ClipboardPayload) {
            when (payload) {
                is ClipboardPayload.Text -> {
                    val selection = StringSelection(payload.value)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                }

                is ClipboardPayload.Image -> {
                    // Legacy desktop receive path handled TEXT only (the phone renders
                    // image payloads itself); nothing to re-write locally.
                }
            }
        }
    }

    fun wsSender(): ClipboardSender = object : ClipboardSender {
        override suspend fun send(payload: ClipboardPayload): Boolean {
            val envelope = ProtocolEnvelope.envelopeOf(MessageTypes.SET_CLIPBOARD) {
                when (payload) {
                    is ClipboardPayload.Text -> put(FieldNames.TEXT, payload.value)

                    is ClipboardPayload.Image -> {
                        put(FieldNames.TEXT, "")
                        put("imageMime", payload.mimeType)
                        put(FieldNames.IMAGE_BASE64, payload.base64Png)
                    }
                }
            }
            val delivered = com.dexstudios.dex.core.network.server.WebSocketConnectionManager
                .broadcastToPaired(envelope)
            if (delivered) {
                (payload as? ClipboardPayload.Text)?.let { ClipboardSyncState.emitSent(it.value) }
            }
            return delivered
            // NOTE: the ADB fallback (Windows, no live WS session) stays in the composeApp
            // sender adapter — it needs AdbManager, which is app-layer tooling.
        }
    }

    /** SHA-256 content hash, Base64-encoded (deterministic echo-guard keys). */
    fun sha256Base64(content: String): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        Base64.getEncoder().encodeToString(digest.digest(content.toByteArray(Charsets.UTF_8)))
    }.getOrDefault(content.hashCode().toString())
}
