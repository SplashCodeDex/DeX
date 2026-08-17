package com.dexstudios.dex.core.network.server

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

object WebSocketConnectionManager {
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    fun register(fingerprint: String, session: WebSocketSession) {
        sessions[fingerprint] = session
    }

    fun unregister(fingerprint: String) {
        sessions.remove(fingerprint)
    }

    suspend fun sendRequest(fingerprint: String, json: String): Boolean {
        val session = sessions[fingerprint] ?: return false
        return try {
            session.send(Frame.Text(json))
            true
        } catch (e: Exception) {
            false
        }
    }
}

object DexRequestStore {
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()

    fun createRequest(requestId: String): CompletableDeferred<JsonObject> {
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[requestId] = deferred
        return deferred
    }

    fun completeRequest(requestId: String, response: JsonObject) {
        pendingRequests.remove(requestId)?.complete(response)
    }

    fun cancelRequest(requestId: String) {
        pendingRequests.remove(requestId)?.cancel()
    }
}
