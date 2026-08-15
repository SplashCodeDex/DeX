package com.dexstudios.dex.network.protocol

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

data class PunchEndpoint(val ip: String, val port: Int)

/**
 * Shared state for direct phone-to-phone (NAT-punched) transfers.
 * Progress reuses the existing upload/download state flows so the transfer overlay works unchanged.
 */
object PunchState {
    /** Same-email devices advertised by the PC — "my devices" reachable over WAN. */
    val devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    /** The WS reply awaited by an in-flight resolve-endpoint request. */
    val pendingEndpointInfo = MutableStateFlow<CompletableDeferred<EndpointInfoDto>?>(null)

    /** The WS reply awaited by an in-flight relay-transfer request (punch-failure fallback). */
    val pendingRelay = MutableStateFlow<CompletableDeferred<Boolean>?>(null)

    /** Public endpoints announced by peers that are about to punch us (peer-endpoint). */
    val incomingPeerEndpoints = MutableStateFlow<Map<String, PunchEndpoint>>(emptyMap())
}

data class ResumeEntry(val docUri: Uri, var received: Long, val size: Long)

/**
 * Receiver-side resume state for direct punch transfers: per-file byte progress and the
 * partial document to append to, surviving connection drops (10-minute window).
 */
object PunchResumeState {
    private val sessions = ConcurrentHashMap<String, ConcurrentHashMap<String, ResumeEntry>>()
    private val acceptedSessions = ConcurrentHashMap<String, Long>()

    fun mapFor(sessionId: String): ConcurrentHashMap<String, ResumeEntry> =
        sessions.getOrPut(sessionId) { ConcurrentHashMap() }

    fun isAccepted(sessionId: String): Boolean = acceptedSessions.containsKey(sessionId)

    fun markAccepted(sessionId: String) {
        acceptedSessions[sessionId] = System.currentTimeMillis()
    }

    fun complete(sessionId: String) {
        sessions.remove(sessionId)
        acceptedSessions.remove(sessionId)
    }

    fun prune() {
        val cutoff = System.currentTimeMillis() - 10 * 60 * 1000
        acceptedSessions.forEach { (sid, lastAccess) ->
            if (lastAccess < cutoff) {
                acceptedSessions.remove(sid)
                sessions.remove(sid)
            }
        }
    }
}
