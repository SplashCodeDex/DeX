package com.dexstudios.dex.core.network

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * End-to-end encryption for WAN relay sessions (plan 032): the relay forwards OPAQUE
 * bytes only — session keys live on the peers, derived here from the shared pairing
 * credential plus the relay session id. A $4 VPS can therefore never read content;
 * this is what makes the thin-relay architecture safe by construction.
 *
 * Key schedule (no hand-rolled crypto anywhere):
 *  - IKM   = the per-device paired token (both peers hold it from the PIN-pairing
 *    exchange; never synced, never advertised).
 *  - salt  = the relay sessionId (server-minted, unique per transfer).
 *  - info  = "dex-relay-v1" (domain separation).
 *  - PRK   = HKDF-Extract(salt, IKM) -> 32-byte AES-256 key via HKDF-Expand.
 *  - AEAD  = AES-256-GCM per chunk: random 12-byte nonce, 16-byte tag. Tampering,
 *    truncation, reordering, or wrong-key decryption all fail closed.
 *
 * expect/actual: JVM uses javax.crypto (AES/GCM/NoPadding + HKDF via HMAC-SHA256
 * extract+expand); future platforms implement with their native APIs. The CHUNK layout
 * on the wire: [12-byte nonce][ciphertext+tag].
 */
object RelayCrypto {

    /** Domain-separation label for HKDF — never reuse a key schedule across purposes. */
    const val HKDF_INFO = "dex-relay-v1"

    /** AES-256 key length (bytes). */
    const val KEY_LENGTH_BYTES = 32

    /** GCM nonce length (bytes) — standard 96-bit. */
    const val NONCE_LENGTH_BYTES = 12

    /**
     * Derives the session key: HKDF(salt = sessionId, IKM = pairedToken, info = "dex-relay-v1").
     * Both peers derive IDENTICALLY — no key ever crosses the wire or the relay.
     */
    fun deriveSessionKey(pairedToken: String, sessionId: String): ByteArray = actualDeriveRelaySessionKey(
        ikm = pairedToken,
        salt = sessionId,
        info = HKDF_INFO,
    )

    /** Seals [plaintext] under [key]; output = [NONCE_LENGTH_BYTES] nonce + ciphertext + GCM tag. */
    fun seal(key: ByteArray, plaintext: ByteArray): ByteArray = actualRelaySeal(key, plaintext)

    /**
     * Seals frame [seq] of the stream: the sequence number rides the GCM AAD, so a
     * duplicated frame (HTTP retry), a reordered frame, or a gap fails authentication
     * on the receiver — silent stream corruption via replay is structurally impossible.
     */
    fun sealFrame(key: ByteArray, seq: Long, plaintext: ByteArray): ByteArray = actualRelaySealFrame(key, seq, plaintext)

    /**
     * Opens a [sealed] chunk. Throws [RelayCryptoException] on ANY failure — wrong key,
     * tampered bytes, truncated chunk — callers treat exceptions as fatal for the chunk.
     */
    fun open(key: ByteArray, sealed: ByteArray): ByteArray = actualRelayOpen(key, sealed)

    /** Opens frame [seq]; wrong/duplicate/replayed sequence numbers fail closed. */
    fun openFrame(key: ByteArray, seq: Long, sealed: ByteArray): ByteArray = actualRelayOpenFrame(key, seq, sealed)
}

/** Sealed-chunk integrity failure: wrong key, tamper, or truncation. */
class RelayCryptoException(message: String) : Exception(message)

// The Base64 helper below is used by tests/verification utilities, not the wire path
// (the relay moves raw opaque bytes; base64 only appears in UI/diagnostics).
@OptIn(ExperimentalEncodingApi::class)
fun encodeBase64(bytes: ByteArray): String = Base64.encode(bytes)

expect fun actualDeriveRelaySessionKey(ikm: String, salt: String, info: String): ByteArray
expect fun actualRelaySeal(key: ByteArray, plaintext: ByteArray): ByteArray
expect fun actualRelayOpen(key: ByteArray, sealed: ByteArray): ByteArray
expect fun actualRelaySealFrame(key: ByteArray, seq: Long, plaintext: ByteArray): ByteArray
expect fun actualRelayOpenFrame(key: ByteArray, seq: Long, sealed: ByteArray): ByteArray
