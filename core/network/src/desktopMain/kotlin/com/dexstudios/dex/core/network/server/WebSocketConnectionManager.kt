package com.dexstudios.dex.core.network.server

import com.dexstudios.dex.core.network.RegisterDto
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

sealed class ConnectionEvent {
    data class Connected(val fingerprint: String) : ConnectionEvent()
    data class Disconnected(val fingerprint: String) : ConnectionEvent()
}

/**
 * A connected control-plane session.
 *
 * [trusted] means the handshake presented proof of trust: the bearer token equaled our
 * googleSub / identityHash (same-account auto-trust) or the fingerprint's paired token.
 * Only trusted sessions receive transfer prompts, hosted-file pushes and roster data;
 * untrusted sessions may only run the pairing handshake until the PIN is proven.
 *
 * [identityToken] records WHICH of our identity tokens the peer presented (when any) so
 * same-email roster membership can be derived without trusting client claims.
 */
class SessionHolder(val session: WebSocketSession, val trusted: Boolean, val identityToken: String?, val mutex: Mutex = Mutex())

object WebSocketConnectionManager {
    private val sessions = ConcurrentHashMap<String, SessionHolder>()

    private val _events = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    /**
     * Registers a session for [fingerprint]. Returns false when an active session for the
     * same fingerprint already exists — the caller must refuse the new connection instead
     * of silently replacing the slot, otherwise any LAN peer that knows a victim's
     * (publicly broadcast) fingerprint could hijack its prompts and pull tokens.
     */
    fun register(fingerprint: String, session: WebSocketSession, trusted: Boolean, identityToken: String? = null): Boolean {
        val added = sessions.putIfAbsent(fingerprint, SessionHolder(session, trusted, identityToken)) == null
        if (added && trusted) {
            _events.tryEmit(ConnectionEvent.Connected(fingerprint))
        }
        return added
    }

    /** Upgrades a session to trusted after the pairing PIN has been proven. */
    fun markTrusted(fingerprint: String, identityToken: String? = null) {
        sessions.computeIfPresent(fingerprint) { _, holder ->
            SessionHolder(holder.session, true, identityToken ?: holder.identityToken, holder.mutex)
        }
        _events.tryEmit(ConnectionEvent.Connected(fingerprint))
    }

    /** Downgrades a session to untrusted (peer-initiated revocation); prompts stop flowing. */
    fun markUntrusted(fingerprint: String) {
        sessions.computeIfPresent(fingerprint) { _, holder ->
            if (!holder.trusted) holder else SessionHolder(holder.session, false, null, holder.mutex)
        }
    }

    fun unregister(fingerprint: String) {
        if (sessions.remove(fingerprint) != null) {
            _events.tryEmit(ConnectionEvent.Disconnected(fingerprint))
        }
    }

    fun isConnected(fingerprint: String): Boolean = sessions.containsKey(fingerprint)

    fun isTrusted(fingerprint: String): Boolean = sessions[fingerprint]?.trusted == true

    fun holderOf(fingerprint: String): SessionHolder? = sessions[fingerprint]

    fun connectedFingerprints(): Set<String> = sessions.keys.toSet()

    fun trustedFingerprints(): Set<String> = sessions.filterValues { it.trusted }.keys.toSet()

    suspend fun sendRequest(fingerprint: String, json: String): Boolean {
        val holder = sessions[fingerprint] ?: return false
        return trySend(holder, json)
    }

    /** Sends only to sessions that completed the trust handshake. */
    suspend fun sendToTrusted(fingerprint: String, json: String): Boolean {
        val holder = sessions[fingerprint] ?: return false
        if (!holder.trusted) return false
        return trySend(holder, json)
    }

    suspend fun broadcastToPaired(json: String): Boolean {
        if (sessions.isEmpty()) return false
        var sentAny = false
        // Session-level proof is mandatory: persistence alone (paired fingerprint on disk)
        // must never qualify a live session whose handshake/identity-proof did not pass —
        // otherwise a reconnecting stranger holding nothing receives clipboard/mirror pushes.
        val trustedFps = sessions.filterValues { it.trusted }.keys
        for ((fp, holder) in sessions) {
            if (fp in trustedFps && holder.trusted) {
                if (trySend(holder, json)) sentAny = true
            }
        }
        return sentAny
    }

    private suspend fun trySend(holder: SessionHolder, json: String): Boolean = try {
        holder.mutex.withLock {
            holder.session.send(Frame.Text(json))
        }
        true
    } catch (_: Exception) {
        false
    }
}

object DexRequestStore {
    /** Unanswered requests older than this are cancelled so vanished peers cannot leak slots. */
    private const val PENDING_TTL_MS = 5 * 60 * 1000L

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val requestTimestamps = ConcurrentHashMap<String, Long>()

    fun createRequest(requestId: String): CompletableDeferred<JsonObject> {
        sweepExpired()
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[requestId] = deferred
        requestTimestamps[requestId] = System.currentTimeMillis()
        return deferred
    }

    fun completeRequest(requestId: String, response: JsonObject) {
        requestTimestamps.remove(requestId)
        pendingRequests.remove(requestId)?.complete(response)
    }

    fun cancelRequest(requestId: String) {
        pendingRequests.remove(requestId)?.cancel()
        requestTimestamps.remove(requestId)
    }

    private fun sweepExpired() {
        val now = System.currentTimeMillis()
        for ((id, ts) in requestTimestamps) {
            if (now - ts > PENDING_TTL_MS) {
                cancelRequest(id)
            }
        }
    }
}
