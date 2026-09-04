package com.dexstudios.dex.network.crypto

/**
 * Android forwarding shim (Plan 046): delegates directly to the shared
 * multiplatform cryptographic engine in [:core:data] (com.dexstudios.dex.core.network.PunchCrypto).
 *
 * Guarantees 100% compile-time and binary parity across Desktop, Android, and future mobile peers.
 */
typealias PunchKeyPair = com.dexstudios.dex.core.network.PunchKeyPair
typealias PunchSessionKeys = com.dexstudios.dex.core.network.PunchSessionKeys

object PunchCrypto {
    const val PUNCH_SESSION_INFO: String = com.dexstudios.dex.core.network.PunchCrypto.PUNCH_SESSION_INFO
    const val PUNCH_AUTH_INFO: String = com.dexstudios.dex.core.network.PunchCrypto.PUNCH_AUTH_INFO
    const val SALT_LENGTH_BYTES: Int = com.dexstudios.dex.core.network.PunchCrypto.SALT_LENGTH_BYTES
    const val KEY_LENGTH_BYTES: Int = com.dexstudios.dex.core.network.PunchCrypto.KEY_LENGTH_BYTES

    fun generateKeyPair(): PunchKeyPair = com.dexstudios.dex.core.network.PunchCrypto.generateKeyPair()

    fun generateSalt(): ByteArray = com.dexstudios.dex.core.network.PunchCrypto.generateSalt()

    fun computeSharedSecret(privateKeyBytes: ByteArray, peerPublicKeyBytes: ByteArray): ByteArray =
        com.dexstudios.dex.core.network.PunchCrypto.computeSharedSecret(privateKeyBytes, peerPublicKeyBytes)

    fun derivePunchKeys(sharedSecret: ByteArray, salt: ByteArray): PunchSessionKeys =
        com.dexstudios.dex.core.network.PunchCrypto.derivePunchKeys(sharedSecret, salt)

    fun computeSenderAuthProof(
        authKey: ByteArray,
        identitySecret: String,
        senderPubKey: ByteArray,
        receiverPubKey: ByteArray,
    ): ByteArray = com.dexstudios.dex.core.network.PunchCrypto.computeSenderAuthProof(
        authKey,
        identitySecret,
        senderPubKey,
        receiverPubKey,
    )

    fun computeReceiverAuthProof(
        authKey: ByteArray,
        identitySecret: String,
        senderPubKey: ByteArray,
        receiverPubKey: ByteArray,
    ): ByteArray = com.dexstudios.dex.core.network.PunchCrypto.computeReceiverAuthProof(
        authKey,
        identitySecret,
        senderPubKey,
        receiverPubKey,
    )

    fun verifyAuthProof(expected: ByteArray, actual: ByteArray): Boolean =
        com.dexstudios.dex.core.network.PunchCrypto.verifyAuthProof(expected, actual)

    fun sealFrame(sessionKey: ByteArray, seq: Long, plaintext: ByteArray): ByteArray =
        com.dexstudios.dex.core.network.PunchCrypto.sealFrame(sessionKey, seq, plaintext)

    fun openFrame(sessionKey: ByteArray, seq: Long, sealed: ByteArray): ByteArray =
        com.dexstudios.dex.core.network.PunchCrypto.openFrame(sessionKey, seq, sealed)
}
