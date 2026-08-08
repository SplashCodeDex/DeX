package com.example.dex.network

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import java.util.UUID

class MessageHandler(
    private val context: Context,
    private val notificationHelper: NotificationHelper
) {
    private val json = Json { ignoreUnknownKeys = true }

    var onSendMessage: ((String) -> Unit)? = null

    fun handleMessage(text: String, senderIp: String, senderPort: Int) {
        try {
            val jsonObject = json.decodeFromString<JsonObject>(text)
            val type = jsonObject["type"]?.jsonPrimitive?.content ?: return
            val dataElement = jsonObject["data"] ?: return

            when (type) {
                "pair-prompt" -> handlePairPrompt(dataElement)
                "prepare-upload" -> handlePrepareUpload(dataElement, senderIp)
                else -> {
                    Timber.w("Unknown message type received: $type")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse WebSocket message")
        }
    }

    private fun handlePairPrompt(dataElement: JsonElement) {
        if (AuthState.incomingPairRequest.value != null) {
            Timber.w("Pairing request already pending, ignoring duplicate")
            return
        }

        val pairReq = json.decodeFromJsonElement<PairRequestDto>(dataElement)
        Timber.i("Incoming pair-prompt via WebSocket from ${pairReq.alias}")
        val info = PairRequestInfo(
            alias = pairReq.alias,
            fingerprint = pairReq.fingerprint,
            pin = pairReq.pin,
            deferred = CompletableDeferred()
        )
        AuthState.incomingPairRequest.value = info
        notificationHelper.showPairingRequestNotification(pairReq.alias)

        CoroutineScope(Dispatchers.Main).launch {
            val enteredPin = withTimeoutOrNull(PAIR_PROMPT_TIMEOUT_MS) { info.deferred.await() }
            AuthState.incomingPairRequest.value = null

            val accepted = enteredPin != null && enteredPin == pairReq.pin
            if (accepted) {
                DeviceManager.savePairedFingerprint(pairReq.fingerprint)
                pairReq.token?.let { DeviceManager.savePairedToken(pairReq.fingerprint, it) }
                Timber.i("Pairing accepted with ${pairReq.alias}")
            } else {
                Timber.i("Pairing rejected or timed out with ${pairReq.alias}")
            }
            sendPairResponse(accepted)
        }
    }

    private fun handlePrepareUpload(dataElement: JsonElement, senderIp: String) {
        val uploadReq = json.decodeFromJsonElement<PrepareUploadRequestDto>(dataElement)
        Timber.i("Incoming prepare-upload via WebSocket from ${uploadReq.info.alias} for ${uploadReq.files.size} files")

        val sessionId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()
        TransferState.pendingPrompts[sessionId] = deferred
        val notificationId = sessionId.hashCode()
        notificationHelper.showIncomingFileNotification(sessionId, notificationId, uploadReq.files.size)

        CoroutineScope(Dispatchers.IO).launch {
            val accepted = withTimeoutOrNull(PROMPT_TIMEOUT_MS) { deferred.await() } == true
            TransferState.pendingPrompts.remove(sessionId)
            if (!accepted) {
                Timber.i("Incoming transfer rejected or timed out")
                return@launch
            }

            var dirUri = SafStorage.getDownloadsDexUri(context)
            if (dirUri == null) {
                Timber.w("Downloads/DeX folder grant missing, prompting user")
                SafStorage.promptForDownloadsDexGrant(context)
                // Wait for the user to grant the folder instead of silently dropping the transfer
                val deadline = System.currentTimeMillis() + GRANT_WAIT_MS
                while (System.currentTimeMillis() < deadline) {
                    delay(500)
                    dirUri = SafStorage.getDownloadsDexUri(context)
                    if (dirUri != null) break
                }
                if (dirUri == null) {
                    Timber.w("User did not grant Downloads/DeX folder; incoming transfer dropped")
                    return@launch
                }
            }

            // Pull mode: download the whole session in one work item (QUIC streams, aggregate progress)
            val files = uploadReq.files.map { (fileId, file) -> PullFileDto(fileId, file.fileName, file.size) }
            TcpDownloadService.downloadBatch(context, senderIp, PULL_PORT, files, dirUri)
        }
    }

    private fun sendPairResponse(accepted: Boolean) {
        val payload = buildJsonObject {
            put("type", "pair-response")
            putJsonObject("data") {
                put("accepted", accepted)
            }
        }
        onSendMessage?.invoke(payload.toString())
    }

    private companion object {
        const val PAIR_PROMPT_TIMEOUT_MS = 60_000L
        const val PROMPT_TIMEOUT_MS = 60_000L
        const val GRANT_WAIT_MS = 180_000L
        const val PULL_PORT = 53319
    }
}
