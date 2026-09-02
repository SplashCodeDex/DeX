package com.dexstudios.dex.network.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Fatal cryptographic or authentication failure during WAN relay stream processing.
 * Tampered frames, replayed frames, incorrect session keys, or truncated data all throw this.
 */
class RelayCryptoException(message: String) : Exception(message)

/**
 * Android client-side E2EE engine for cloud relay transfers (Plan 032 / Option 3).
 *
 * Law: The relay postman never sees plaintext. Content is sealed using AES-256-GCM
 * with HKDF-SHA256 session keys derived from the shared PIN-pairing secret ([pairedToken])
 * and the server-assigned [sessionId].
 *
 * Wire layout per chunk:
 *   [12-byte random nonce] [AES-256-GCM ciphertext + 16-byte tag (128-bit)]
 *
 * Sequenced AAD:
 *   Each frame is authenticated with its 8-byte big-endian sequence number in the GCM AAD.
 *   Any replayed, reordered, or dropped frame fails authentication immediately.
 */
object RelayCrypto {

    /** Domain-separation label for HKDF — never reuse a key schedule across purposes. */
    const val HKDF_INFO = "dex-relay-v1"

    /** AES-256 key length (bytes). */
    const val KEY_LENGTH_BYTES = 32

    /** GCM nonce length (bytes) — standard 96-bit. */
    const val NONCE_LENGTH_BYTES = 12

    /** GCM auth tag bit length. */
    private const val GCM_TAG_BITS = 128

    /**
     * Derives the session key: HKDF-Extract-then-Expand (RFC 5869).
     *   PRK = HMAC-SHA256(salt = sessionId, IKM = pairedToken)
     *   OKM = HMAC-SHA256(PRK, info || 0x01) -> 32 bytes
     *
     * Both sender and receiver derive the exact same 256-bit key independently.
     * No key material is ever transmitted over the wire or to the relay.
     */
    fun deriveSessionKey(pairedToken: String, sessionId: String): ByteArray {
        require(pairedToken.isNotBlank()) { "pairedToken required for session key derivation" }
        require(sessionId.isNotBlank()) { "sessionId required for session key derivation" }

        // HKDF-Extract: PRK = HMAC-SHA256(salt, IKM)
        val extractMac = Mac.getInstance("HmacSHA256")
        extractMac.init(SecretKeySpec(sessionId.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val prk = extractMac.doFinal(pairedToken.toByteArray(Charsets.UTF_8))

        // HKDF-Expand: T(1) = HMAC-SHA256(PRK, info || 0x01), returning exactly 32 bytes
        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        expandMac.update(HKDF_INFO.toByteArray(Charsets.UTF_8))
        expandMac.update(1.toByte())
        val okm = expandMac.doFinal()

        require(okm.size == KEY_LENGTH_BYTES) { "HKDF produced ${okm.size} bytes (expected $KEY_LENGTH_BYTES)" }
        return okm
    }

    /**
     * Seals frame [seq] of the stream with AES-256-GCM.
     * The sequence number rides the GCM AAD.
     */
    fun sealFrame(key: ByteArray, seq: Long, plaintext: ByteArray): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "relay session key must be 256-bit" }
        val nonce = ByteArray(NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(seqAad(seq))
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    /**
     * Opens frame [seq] of the stream.
     * Throws [RelayCryptoException] if:
     * - Frame is shorter than [NONCE_LENGTH_BYTES]
     * - Frame sequence number in AAD does not match [seq] (replay/reorder/gap)
     * - GCM auth tag does not verify (tampering, wrong key, truncated ciphertext)
     */
    fun openFrame(key: ByteArray, seq: Long, sealed: ByteArray): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "relay session key must be 256-bit" }
        if (sealed.size <= NONCE_LENGTH_BYTES) {
            throw RelayCryptoException("sealed frame too short (${sealed.size} bytes)")
        }
        val nonce = sealed.copyOfRange(0, NONCE_LENGTH_BYTES)
        val ciphertext = sealed.copyOfRange(NONCE_LENGTH_BYTES, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(seqAad(seq))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            // Fails closed on wrong sequence, replayed frame, reorder, tamper, or wrong key.
            throw RelayCryptoException("frame failed authentication (seq=$seq): ${e.javaClass.simpleName}")
        }
    }

    /**
     * Unsequenced seal (single chunk).
     */
    fun seal(key: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "relay session key must be 256-bit" }
        val nonce = ByteArray(NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    /**
     * Unsequenced open (single chunk).
     */
    fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "relay session key must be 256-bit" }
        if (sealed.size <= NONCE_LENGTH_BYTES) {
            throw RelayCryptoException("sealed chunk too short (${sealed.size} bytes)")
        }
        val nonce = sealed.copyOfRange(0, NONCE_LENGTH_BYTES)
        val ciphertext = sealed.copyOfRange(NONCE_LENGTH_BYTES, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw RelayCryptoException("chunk failed authentication: ${e.javaClass.simpleName}")
        }
    }

    /** Encodes 64-bit sequence number as big-endian 8 bytes for GCM AAD binding. */
    private fun seqAad(seq: Long): ByteArray = byteArrayOf(
        (seq ushr 56).toByte(),
        (seq ushr 48).toByte(),
        (seq ushr 40).toByte(),
        (seq ushr 32).toByte(),
        (seq ushr 24).toByte(),
        (seq ushr 16).toByte(),
        (seq ushr 8).toByte(),
        seq.toByte(),
    )
}
