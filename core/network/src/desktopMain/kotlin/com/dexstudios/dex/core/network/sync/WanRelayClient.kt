package com.dexstudios.dex.core.network.sync

import com.dexstudios.dex.core.network.RelayCrypto
import com.dexstudios.dex.core.protocol.FieldNames
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream

/**
 * Desktop WAN relay client (plan 032): orchestrates an E2EE transfer through the cloud
 * relay. THE LAW: content is sealed with [RelayCrypto] BEFORE the first byte leaves this
 * process — the relay (and anyone observing it) only ever sees opaque bytes.
 *
 * Wire framing: each sealed chunk is prefixed with its 4-byte big-endian length before
 * it enters the relay stream. The relay forwards the framing verbatim — it learns only
 * chunk sizes (which it already accounts for quota-wise), never content.
 *
 * Memory discipline: BOTH halves stream — the sender reads the source in [CHUNK_SIZE]
 * pieces and the receiver decrypts frame-by-frame straight to [OutputStream]. Neither
 * side ever holds the whole file (a 10 GiB WAN transfer must cost KiB, not GiB).
 *
 * Auth: caller supplies the live Google ID token + relay base URL (settings).
 */
class WanRelayClient(private val client: HttpClient, private val baseUrlProvider: () -> String, private val tokenProvider: suspend () -> String) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** Plaintext bytes per sealed chunk (server quota accounts the sealed size). */
        const val CHUNK_SIZE = 256 * 1024

        /** Length-prefix bytes framing every sealed chunk. */
        const val LENGTH_PREFIX_BYTES = 4

        /** Upper bound of a legal frame: sealed chunk = nonce + tag + CHUNK_SIZE plaintext. */
        const val MAX_FRAME_BYTES = CHUNK_SIZE + RelayCrypto.NONCE_LENGTH_BYTES + 64

        /** Control-plane timeout: session open/complete/close + per-frame pushes. */
        const val REQUEST_TIMEOUT_MS = 120_000L

        /**
         * Streaming download guards: connect + inactivity bounds ONLY — a whole-request
         * timeout would abort any transfer whose total duration exceeds it (a multi-GB
         * file over a slow uplink legitimately runs for hours). The 60s socket timeout
         * still catches a dead relay mid-stream; the server's session TTLs bound runaway.
         */
        const val STREAM_CONNECT_TIMEOUT_MS = 10_000L

        const val STREAM_SOCKET_TIMEOUT_MS = 60_000L
    }

    /** A relay session opened for one E2EE transfer. */
    data class RelaySession(val sessionId: String, val streamToken: String, val targetDeviceId: String)

    /** Opens a relay session for a transfer toward [targetDeviceId]. */
    suspend fun openSession(targetDeviceId: String): RelaySession {
        val base = baseUrlProvider().trim().trimEnd('/')
        require(base.isNotBlank()) { "no relay host configured (Settings)" }
        require(targetDeviceId.isNotBlank()) { "targetDeviceId required" }
        val token = tokenProvider()
        require(token.isNotBlank()) { "sign in required for WAN relay" }

        val response = client.post("$base/relay/v1/session?targetDeviceId=$targetDeviceId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("relay session open failed: HTTP ${response.status.value}")
        }
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val sessionId = body[FieldNames.SESSION_ID]?.jsonPrimitive?.content
            ?: throw IllegalStateException("relay session response missing sessionId")
        val streamToken = body[FieldNames.STREAM_TOKEN]?.jsonPrimitive?.content
            ?: throw IllegalStateException("relay session response missing streamToken")
        return RelaySession(sessionId, streamToken, targetDeviceId)
    }

    /**
     * Sender half: streams [input] into the relay as [len][sealed] frames, then signals
     * completion so the receiver's drain terminates. On FAILURE the session is closed
     * (never completed — completing a partial stream would let the receiver accept a
     * truncated file as complete). The session key derives from [pairedToken] +
     * sessionId — never transmitted.
     */
    suspend fun upload(session: RelaySession, pairedToken: String, input: InputStream, onProgress: (bytesSent: Long) -> Unit = {}) {
        require(pairedToken.isNotBlank()) { "paired token required — unpaired devices cannot relay" }
        val key = RelayCrypto.deriveSessionKey(pairedToken, session.sessionId)
        val base = baseUrlProvider().trim().trimEnd('/')
        val buffer = ByteArray(CHUNK_SIZE)
        var total = 0L
        var seq = 0L
        try {
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                val plaintext = if (read == buffer.size) buffer else buffer.copyOf(read)
                // Frame-sequenced AEAD: a replayed/reordered frame fails on the receiver.
                val sealed = RelayCrypto.sealFrame(key, seq, plaintext)
                if (!relayPush(base, session, frame(sealed))) {
                    throw IllegalStateException("relay upload rejected mid-stream (quota or TTL)")
                }
                seq++
                total += read
                onProgress(total)
            }
            // Only a fully-pushed stream is completed; the receiver then finishes cleanly.
            if (!signalComplete(base, session)) {
                throw IllegalStateException("relay completion signal failed")
            }
        } catch (e: Exception) {
            // Failure path: hard-close so no receiver can mistake a partial stream for a
            // complete one, and the tenant's session quota is released at once.
            runCatching { closeSession(session) }
            throw e
        }
    }

    /** Tells the relay the sender's stream is finished (closes the frame channel). */
    private suspend fun signalComplete(base: String, session: RelaySession): Boolean = try {
        val response = client.post("$base/relay/v1/session/${session.sessionId}/complete") {
            header("X-DeX-Stream-Token", session.streamToken)
            timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
        }
        response.status.isSuccess()
    } catch (_: Exception) {
        false
    }

    /** Hard session teardown (quota release after failed transfers). Idempotent; best-effort. */
    suspend fun closeSession(session: RelaySession) {
        val base = baseUrlProvider().trim().trimEnd('/')
        runCatching {
            client.post("$base/relay/v1/session/${session.sessionId}/close") {
                header("X-DeX-Stream-Token", session.streamToken)
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
            }
        }
    }

    /**
     * Receiver half: pulls the opaque frame stream for [session] and writes opened
     * plaintext to [output] FRAME BY FRAME — the whole file is never held in memory.
     * A [com.dexstudios.dex.core.network.RelayCryptoException] (tamper / wrong key /
     * truncation / hostile framing) aborts; the partial output is the caller's to discard.
     */
    suspend fun download(session: RelaySession, pairedToken: String, output: OutputStream, onProgress: (bytesReceived: Long) -> Unit = {}) {
        require(pairedToken.isNotBlank()) { "paired token required — unpaired devices cannot relay" }
        val key = RelayCrypto.deriveSessionKey(pairedToken, session.sessionId)
        val base = baseUrlProvider().trim().trimEnd('/')
        val response = client.get("$base/relay/v1/session/${session.sessionId}/data?streamToken=${session.streamToken}") {
            // Streaming guards: connect + inactivity only (a multi-GB pull can legally
            // run for hours; a whole-request timeout here would kill it mid-stream).
            timeout {
                connectTimeoutMillis = STREAM_CONNECT_TIMEOUT_MS
                socketTimeoutMillis = STREAM_SOCKET_TIMEOUT_MS
                requestTimeoutMillis = null
            }
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("relay download failed: HTTP ${response.status.value}")
        }
        val channel: ByteReadChannel = response.bodyAsChannel()
        var received = 0L
        var expectedSeq = 0L
        while (true) {
            val prefix = channel.readExactOrNull(LENGTH_PREFIX_BYTES)
                ?: break // clean EOF between frames: the sender completed the stream
            val frameLength = decodeFrameLength(prefix)
            if (frameLength <= 0 || frameLength > MAX_FRAME_BYTES) {
                throw com.dexstudios.dex.core.network.RelayCryptoException("hostile frame length: $frameLength")
            }
            val sealed = channel.readExactOrNull(frameLength)
                ?: throw com.dexstudios.dex.core.network.RelayCryptoException("relay stream ended mid-frame")
            // Sequenced open: duplicate/reordered/gapped frames fail authentication.
            val plaintext = RelayCrypto.openFrame(key, expectedSeq, sealed)
            output.write(plaintext)
            expectedSeq++
            received += plaintext.size
            onProgress(received)
        }
    }

    /** Reads exactly [count] bytes; null on clean EOF before ANY byte (stream end). */
    private suspend fun ByteReadChannel.readExactOrNull(count: Int): ByteArray? {
        val out = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val packet = readRemaining((count - filled).toLong())
            if (packet.exhausted()) {
                if (filled == 0) return null
                throw com.dexstudios.dex.core.network.RelayCryptoException("relay stream ended mid-frame")
            }
            val chunk = packet.readBytes()
            chunk.copyInto(out, filled)
            filled += chunk.size
        }
        return out
    }

    private fun decodeFrameLength(prefix: ByteArray): Int = ((prefix[0].toInt() and 0xFF) shl 24) or
        ((prefix[1].toInt() and 0xFF) shl 16) or
        ((prefix[2].toInt() and 0xFF) shl 8) or
        (prefix[3].toInt() and 0xFF)

    private fun frame(sealed: ByteArray): ByteArray {
        val out = ByteArray(LENGTH_PREFIX_BYTES + sealed.size)
        out[0] = (sealed.size ushr 24).toByte()
        out[1] = (sealed.size ushr 16).toByte()
        out[2] = (sealed.size ushr 8).toByte()
        out[3] = sealed.size.toByte()
        sealed.copyInto(out, LENGTH_PREFIX_BYTES)
        return out
    }

    private suspend fun relayPush(base: String, session: RelaySession, frame: ByteArray): Boolean = try {
        val response = client.post("$base/relay/v1/session/${session.sessionId}/data") {
            header("X-DeX-Stream-Token", session.streamToken)
            contentType(ContentType.Application.OctetStream)
            setBody(frame)
            timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
        }
        response.status.isSuccess()
    } catch (_: Exception) {
        false
    }
}
