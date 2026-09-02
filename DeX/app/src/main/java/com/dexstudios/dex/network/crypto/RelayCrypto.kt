package com.dexstudios.dex.network.crypto

/**
 * Android forwarding shim (Plan 030 Phase 2): delegates directly to the shared
 * multiplatform cryptographic engine in [:core:data] (com.dexstudios.dex.core.network.RelayCrypto).
 *
 * This guarantees complete compile-time parity across Desktop, Android, and future mobile peers,
 * while maintaining 100% source-level compatibility for existing Android call sites.
 */
typealias RelayCryptoException = com.dexstudios.dex.core.network.RelayCryptoException

object RelayCrypto {
    const val HKDF_INFO: String = com.dexstudios.dex.core.network.RelayCrypto.HKDF_INFO
    const val KEY_LENGTH_BYTES: Int = com.dexstudios.dex.core.network.RelayCrypto.KEY_LENGTH_BYTES
    const val NONCE_LENGTH_BYTES: Int = com.dexstudios.dex.core.network.RelayCrypto.NONCE_LENGTH_BYTES

    fun deriveSessionKey(pairedToken: String, sessionId: String): ByteArray =
        com.dexstudios.dex.core.network.RelayCrypto.deriveSessionKey(pairedToken, sessionId)

    fun sealFrame(key: ByteArray, seq: Long, plaintext: ByteArray): ByteArray =
        com.dexstudios.dex.core.network.RelayCrypto.sealFrame(key, seq, plaintext)

    fun openFrame(key: ByteArray, seq: Long, sealed: ByteArray): ByteArray =
        com.dexstudios.dex.core.network.RelayCrypto.openFrame(key, seq, sealed)

    fun seal(key: ByteArray, plaintext: ByteArray): ByteArray =
        com.dexstudios.dex.core.network.RelayCrypto.seal(key, plaintext)

    fun open(key: ByteArray, sealed: ByteArray): ByteArray =
        com.dexstudios.dex.core.network.RelayCrypto.open(key, sealed)
}
