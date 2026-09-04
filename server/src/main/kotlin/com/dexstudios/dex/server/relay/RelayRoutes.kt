package com.dexstudios.dex.server.relay

import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.server.auth.IdTokenVerifier
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.readBytes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * STREAMING relay core (plan 032): bounded-memory pass-through for WAN transfers.
 *
 * THE LAW (plan 032 STOP conditions):
 *  - NEVER stage content to disk. Bytes flow sender -> bounded SharedFlow -> receiver.
 *  - E2EE by construction: the relay handles OPAQUE encrypted bytes + routing headers;
 *    session keys live on the peers (pairing identity exchange) — this process CANNOT
 *    read content, which is what makes a small VPS architecturally sufficient forever.
 *  - Quotas enforced BEFORE first byte: per-tenant concurrent session cap, per-session
 *    size cap (checked per chunk, aborting at the boundary), idle + hard TTLs.
 *
 * Flow (REST framing, first cut):
 *  1. POST /relay/v1/session?targetDeviceId=... -> {sessionId, streamToken} (bearer auth)
 *  2. POST /relay/v1/session/{sessionId}/data   (sender streams opaque E2EE bytes)
 *  3. GET  /relay/v1/session/{sessionId}/data   (target pulls the opaque stream)
 *  4. The session dies on completion, cap breach, or timeout — whichever comes first.
 */
object RelaySessionRegistry {

    class QuotaExceeded(message: String) : IllegalStateException(message)

    internal class Session(val sessionId: String, val tenant: String, val streamToken: String, val createdAt: Long) {
        @Volatile
        var bytesRelayed: Long = 0L

        @Volatile
        var lastActivityAt: Long = createdAt
    }

    private const val MAX_CONCURRENT_SESSIONS_PER_TENANT = 2
    private const val MAX_SESSION_BYTES = 2L * 1024 * 1024 * 1024 // 2 GiB per session
    private const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L // desktop RelayService parity
    private const val SESSION_TTL_MS = 60 * 60 * 1000L // 1 h hard lifetime

    /**
     * Bounded frame buffer per session: at most 64 sealed chunks in flight (~16 MiB at
     * the client's 256 KiB chunks). A sender outrunning its receiver SUSPENDS here —
     * that suspension IS the bounded-memory law (never stage to disk, never unbounded).
     */
    private const val FRAME_BUFFER = 64

    private val sessions = ConcurrentHashMap<String, Session>()
    private val frames = ConcurrentHashMap<String, kotlinx.coroutines.channels.Channel<ByteArray>>()

    // Guard for quota check-then-act: parallel opens must not both pass the count.
    // openSession throws [QuotaExceeded] BEFORE any byte can move.
    private val openLock = Any()

    fun openSession(tenant: String): Pair<String, String> {
        synchronized(openLock) {
            sweep()
            val active = sessions.values.count { it.tenant == tenant }
            if (active >= MAX_CONCURRENT_SESSIONS_PER_TENANT) {
                throw QuotaExceeded("concurrent relay session cap reached ($MAX_CONCURRENT_SESSIONS_PER_TENANT)")
            }
            val sessionId = UUID.randomUUID().toString()
            val streamToken = UUID.randomUUID().toString()
            sessions[sessionId] = Session(sessionId, tenant, streamToken, System.currentTimeMillis())
            frames[sessionId] = kotlinx.coroutines.channels.Channel(capacity = FRAME_BUFFER)
            return sessionId to streamToken
        }
    }

    /** Verifies the stream token + TTLs; throws [QuotaExceeded] when unavailable. */
    internal fun requireOpenSession(sessionId: String, streamToken: String): Session {
        val session = sessions[sessionId] ?: throw QuotaExceeded("unknown relay session")
        if (session.streamToken != streamToken) throw QuotaExceeded("bad relay stream token")
        val now = System.currentTimeMillis()
        if (now - session.createdAt > SESSION_TTL_MS || now - session.lastActivityAt > IDLE_TIMEOUT_MS) {
            close(sessionId)
            throw QuotaExceeded("relay session expired")
        }
        return session
    }

    /** Accounts [count] bytes; closes + returns false the moment the cap is breached. */
    internal fun accountBytes(sessionId: String, count: Long): Boolean {
        val session = sessions[sessionId] ?: return false
        session.lastActivityAt = System.currentTimeMillis()
        session.bytesRelayed += count
        if (session.bytesRelayed > MAX_SESSION_BYTES) {
            close(sessionId)
            return false
        }
        return true
    }

    /** The session's frame channel (sends suspend at capacity; drains end at close). */
    internal fun framesFor(sessionId: String): kotlinx.coroutines.channels.Channel<ByteArray>? = frames[sessionId]

    /**
     * Sender signals the stream is complete: the channel closes and the receiver's drain
     * loop terminates cleanly after the last buffered frame. Idempotent.
     */
    fun completeSession(sessionId: String) {
        frames[sessionId]?.close()
    }

    /** Hard teardown (quota breach / TTL / failure): buffered frames are dropped. */
    fun close(sessionId: String) {
        sessions.remove(sessionId)
        frames.remove(sessionId)?.cancel()
    }

    private fun sweep() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { (id, s) ->
            val expired = now - s.createdAt > SESSION_TTL_MS || now - s.lastActivityAt > IDLE_TIMEOUT_MS
            if (expired) frames.remove(id)?.cancel()
            expired
        }
    }

    /** Test seam. */
    fun activeSessionsFor(tenant: String): Int = sessions.values.count { it.tenant == tenant }
}

/** Relay REST surface: bearer auth opens the session; the data plane runs on the stream token. */
fun Route.relayRoutes(verifier: IdTokenVerifier) {
    route("/relay/v1") {
        post("/session") {
            val tenant = call.requireTenant(verifier) ?: return@post
            val target = call.request.queryParameters["targetDeviceId"]
            if (target.isNullOrBlank()) {
                call.respondError(HttpStatusCode.BadRequest, "targetDeviceId required")
                return@post
            }
            try {
                val (sessionId, streamToken) = RelaySessionRegistry.openSession(tenant)
                val payload = buildJsonObject {
                    put(FieldNames.SESSION_ID, sessionId)
                    put(FieldNames.STREAM_TOKEN, streamToken)
                    put("targetDeviceId", target)
                }.toString()
                call.respondJson(payload)
            } catch (e: RelaySessionRegistry.QuotaExceeded) {
                call.respondError(HttpStatusCode.TooManyRequests, e.message ?: "quota exceeded")
            }
        }

        post("/session/{sessionId}/data") {
            val sessionId = call.parameters["sessionId"]
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "sessionId required")
            val streamToken = call.request.header("X-DeX-Stream-Token")
                ?: call.request.queryParameters[FieldNames.STREAM_TOKEN]
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "streamToken required")
            val session = try {
                RelaySessionRegistry.requireOpenSession(sessionId, streamToken)
            } catch (e: RelaySessionRegistry.QuotaExceeded) {
                return@post call.respondError(HttpStatusCode.Gone, e.message ?: "session unavailable")
            }

            val frameChannel = RelaySessionRegistry.framesFor(sessionId)
                ?: return@post call.respondError(HttpStatusCode.Gone, "relay channel vanished")

            try {
                pumpSenderToChannel(call.receiveChannel(), sessionId, frameChannel)
                call.respondJson("""{"relayed":true,"bytes":${session.bytesRelayed}}""")
            } catch (_: Exception) {
                RelaySessionRegistry.close(sessionId)
                call.respondError(HttpStatusCode.Gone, "relay stream failed")
            }
        }

        post("/session/{sessionId}/complete") {
            val sessionId = call.parameters["sessionId"]
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "sessionId required")
            val streamToken = call.request.header("X-DeX-Stream-Token")
                ?: call.request.queryParameters[FieldNames.STREAM_TOKEN]
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "streamToken required")
            try {
                RelaySessionRegistry.requireOpenSession(sessionId, streamToken)
            } catch (e: RelaySessionRegistry.QuotaExceeded) {
                return@post call.respondError(HttpStatusCode.Gone, e.message ?: "session unavailable")
            }
            RelaySessionRegistry.completeSession(sessionId)
            call.respondJson("""{"completed":true}""")
        }

        post("/session/{sessionId}/close") {
            val sessionId = call.parameters["sessionId"]
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "sessionId required")
            val streamToken = call.request.header("X-DeX-Stream-Token")
                ?: call.request.queryParameters[FieldNames.STREAM_TOKEN]
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "streamToken required")
            try {
                RelaySessionRegistry.requireOpenSession(sessionId, streamToken)
            } catch (_: RelaySessionRegistry.QuotaExceeded) {
                // Already gone/expired: close is idempotent — report success.
            }
            RelaySessionRegistry.close(sessionId)
            call.respondJson("""{"closed":true}""")
        }

        get("/session/{sessionId}/data") {
            val sessionId = call.parameters["sessionId"]
                ?: return@get call.respondError(HttpStatusCode.BadRequest, "sessionId required")
            val streamToken = call.request.queryParameters["streamToken"]
                ?: return@get call.respondError(HttpStatusCode.BadRequest, "streamToken required")
            try {
                RelaySessionRegistry.requireOpenSession(sessionId, streamToken)
            } catch (e: RelaySessionRegistry.QuotaExceeded) {
                return@get call.respondError(HttpStatusCode.Gone, e.message ?: "session unavailable")
            }
            val frameChannel = RelaySessionRegistry.framesFor(sessionId)
                ?: return@get call.respondError(HttpStatusCode.Gone, "relay channel vanished")

            // Opaque E2EE frames drain until the sender completes the session: the
            // channel close terminates this loop — the receiver ALWAYS finishes.
            call.respondBytesWriter(contentType = ContentType.Application.OctetStream) {
                try {
                    for (frame in frameChannel) {
                        writeFully(frame)
                    }
                } finally {
                    // If the receiver drops mid-stream or finishes, close the session
                    // so the sender channel is cancelled (unblocking any suspended send)
                    // and tenant quota is instantly released.
                    RelaySessionRegistry.close(sessionId)
                }
            }
        }
    }
}

/** Reads the sender's request body chunk-by-chunk into the bounded relay channel. */
private suspend fun pumpSenderToChannel(channel: ByteReadChannel, sessionId: String, frameChannel: kotlinx.coroutines.channels.Channel<ByteArray>) {
    val chunkSize = 256 * 1024
    while (!channel.isClosedForRead) {
        val packet = channel.readRemaining(chunkSize.toLong())
        if (packet.exhausted()) break
        val chunk = packet.readBytes()
        if (!RelaySessionRegistry.accountBytes(sessionId, chunk.size.toLong())) {
            throw RelaySessionRegistry.QuotaExceeded("session size cap exceeded")
        }
        // Capacity-bounded: an outrunning sender suspends here — bounded memory, by law.
        frameChannel.send(chunk)
    }
}

/** Resolves the tenant from the bearer token; responds 401 and returns null when invalid. */
internal suspend fun ApplicationCall.requireTenant(verifier: IdTokenVerifier): String? {
    val bearer = request.header("Authorization")?.removePrefix("Bearer ")?.trim()
    if (bearer.isNullOrBlank()) {
        respondError(HttpStatusCode.Unauthorized, "missing bearer token")
        return null
    }
    val tenant = verifier.verifyToken(bearer)
    if (tenant == null) {
        respondError(HttpStatusCode.Unauthorized, "invalid or expired ID token")
        return null
    }
    return tenant
}

internal suspend fun ApplicationCall.respondError(status: HttpStatusCode, msg: String) {
    respondText(msg, status = status)
}

internal suspend fun ApplicationCall.respondJson(body: String) {
    respondText(body, ContentType.Application.Json)
}
