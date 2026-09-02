package com.dexstudios.dex.core.network

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// RelayCrypto Android actuals: standard javax.crypto hardware-accelerated AES-256-GCM AEAD
// and HKDF-SHA256 key derivation.

private const val GCM_TAG_BITS = 128

actual fun actualDeriveRelaySessionKey(ikm: String, salt: String, info: String): ByteArray {
    // HKDF-Extract: PRK = HMAC-SHA256(salt, IKM)
    val extractMac = Mac.getInstance("HmacSHA256")
    extractMac.init(SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val prk = extractMac.doFinal(ikm.toByteArray(Charsets.UTF_8))

    // HKDF-Expand: T(1) = HMAC-SHA256(PRK, info || 0x01), returning exactly 32 bytes
    val expandMac = Mac.getInstance("HmacSHA256")
    expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
    expandMac.update(info.toByteArray(Charsets.UTF_8))
    expandMac.update(1.toByte())
    val okm = expandMac.doFinal()
    require(okm.size == RelayCrypto.KEY_LENGTH_BYTES) { "HKDF produced ${okm.size} bytes" }
    return okm
}

actual fun actualRelaySeal(key: ByteArray, plaintext: ByteArray): ByteArray {
    require(key.size == RelayCrypto.KEY_LENGTH_BYTES) { "relay session key must be 256-bit" }
    val nonce = ByteArray(RelayCrypto.NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
    val ciphertext = cipher.doFinal(plaintext)
    return nonce + ciphertext
}

actual fun actualRelayOpen(key: ByteArray, sealed: ByteArray): ByteArray {
    require(key.size == RelayCrypto.KEY_LENGTH_BYTES) { "relay session key must be 256-bit" }
    if (sealed.size <= RelayCrypto.NONCE_LENGTH_BYTES) {
        throw RelayCryptoException("sealed chunk too short (${sealed.size} bytes)")
    }
    val nonce = sealed.copyOfRange(0, RelayCrypto.NONCE_LENGTH_BYTES)
    val ciphertext = sealed.copyOfRange(RelayCrypto.NONCE_LENGTH_BYTES, sealed.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
    return try {
        cipher.doFinal(ciphertext)
    } catch (e: Exception) {
        throw RelayCryptoException("chunk failed authentication: ${e.javaClass.simpleName}")
    }
}

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

actual fun actualRelaySealFrame(key: ByteArray, seq: Long, plaintext: ByteArray): ByteArray {
    require(key.size == RelayCrypto.KEY_LENGTH_BYTES) { "relay session key must be 256-bit" }
    val nonce = ByteArray(RelayCrypto.NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
    cipher.updateAAD(seqAad(seq))
    val ciphertext = cipher.doFinal(plaintext)
    return nonce + ciphertext
}

actual fun actualRelayOpenFrame(key: ByteArray, seq: Long, sealed: ByteArray): ByteArray {
    require(key.size == RelayCrypto.KEY_LENGTH_BYTES) { "relay session key must be 256-bit" }
    if (sealed.size <= RelayCrypto.NONCE_LENGTH_BYTES) {
        throw RelayCryptoException("sealed frame too short (${sealed.size} bytes)")
    }
    val nonce = sealed.copyOfRange(0, RelayCrypto.NONCE_LENGTH_BYTES)
    val ciphertext = sealed.copyOfRange(RelayCrypto.NONCE_LENGTH_BYTES, sealed.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
    cipher.updateAAD(seqAad(seq))
    return try {
        cipher.doFinal(ciphertext)
    } catch (e: Exception) {
        throw RelayCryptoException("frame failed authentication (seq=$seq): ${e.javaClass.simpleName}")
    }
}
