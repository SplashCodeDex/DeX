package com.dexstudios.dex.desktop.jna

import com.dexstudios.dex.core.domain.clipboard.ClipboardPayload
import com.dexstudios.dex.core.domain.clipboard.ClipboardSender
import com.dexstudios.dex.core.network.ClipboardSyncState
import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.core.protocol.MessageTypes
import com.dexstudios.dex.core.protocol.ProtocolEnvelope
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * ComposeApp clipboard sender (plan 029): the WS sender from core/network wrapped with
 * the ADB fallback — broadcast first; when no peer session is live, fall back to the
 * bundled platform-tools broadcast (bounded process, never a bare PATH adb exec).
 */
object DesktopClipboardSender : ClipboardSender {

    private val wsSender = com.dexstudios.dex.core.network.sync.DesktopClipboardPorts.wsSender()

    override suspend fun send(payload: ClipboardPayload): Boolean {
        if (wsSender.send(payload)) return true

        // ADB Fallback — TEXT lane only (the phone renders image payloads itself).
        val text = (payload as? ClipboardPayload.Text)?.value ?: return false
        val b64 = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        val delivered = com.dexstudios.dex.desktop.AdbManager.broadcast(
            action = "com.dexstudios.dex.SET_CLIPBOARD",
            extras = mapOf("text_b64" to b64),
        )
        if (delivered) {
            ClipboardSyncState.emitSent(text)
        }
        return delivered
    }
}

/** Envelope builder retained for the ADB fallback path's text lane. */
fun buildClipboardEnvelope(text: String): String = ProtocolEnvelope.envelopeOf(MessageTypes.SET_CLIPBOARD) {
    put(FieldNames.TEXT, text)
}
