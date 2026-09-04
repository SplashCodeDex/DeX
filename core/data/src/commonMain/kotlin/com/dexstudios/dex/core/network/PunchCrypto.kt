package com.dexstudios.dex.core.network

/**
 * Ephemeral EC key pair for direct punch key agreement.
 * [publicKeyBytes] is X.509 SubjectPublicKeyInfo encoded.
 * [privateKeyBytes] is PKCS#8 encoded.
 */
class PunchKeyPair(val publicKeyBytes: ByteArray, val privateKeyBytes: ByteArray)

/**
 * Derived keys for a single direct punch transfer session.
 * [sessionKey]: 256-bit AES-GCM key for frame encryption.
 * [authKey]: 256-bit HMAC key for mutual proof-of-identity.
 */
class PunchSessionKeys(val sessionKey: ByteArray, val authKey: ByteArray)

/**
 * End-to-end encryption and mutual authentication for direct NAT punch transfers (Plan 046).
 *
 * Trust and Handshake Model:
 *  1. Ephemeral NIST P-256 (secp256r1) key exchange: sender and receiver generate single-use
 *     keypairs per transfer connection, achieving Perfect Forward Secrecy (PFS).
 *  2. HKDF-SHA256 key derivation with salt and domain-separated labels ("dex-punch-session-v1"
 *     and "dex-punch-auth-v1") guarantees no key reuse.
 *  3. Mutual proof-of-identity via HMAC-SHA256 over [identityHash] bound to both ephemeral
 *     public keys. An on-path attacker or hostile NAT cannot MITM or substitute public keys
 *     without knowing the shared identity credential.
 *  4. Sequenced AEAD data plane: all frames (manifest, resume info, file headers, and chunks)
 *     are sealed using AES-256-GCM with sequence numbers bound in AAD via [RelayCrypto.sealFrame].
 *     Replay, reordering, truncation, or bit tampering fail closed immediately.
 */
object PunchCrypto {

    const val PUNCH_SESSION_INFO = "dex-punch-session-v1"
    const val PUNCH_AUTH_INFO = "dex-punch-auth-v1"
    const val SALT_LENGTH_BYTES = 16
    const val KEY_LENGTH_BYTES = 32

    /** Generates an ephemeral NIST P-256 (secp256r1) keypair. */
    fun generateKeyPair(): PunchKeyPair = actualPunchGenerateKeyPair()

    /** Generates a cryptographically secure 16-byte random salt. */
    fun generateSalt(): ByteArray = actualPunchGenerateSalt()

    /** Computes the raw ECDH shared secret from our private key and peer's public key. */
    fun computeSharedSecret(privateKeyBytes: ByteArray, peerPublicKeyBytes: ByteArray): ByteArray = actualPunchComputeSharedSecret(privateKeyBytes, peerPublicKeyBytes)

    /** Derives session and authentication keys via HKDF-SHA256 from the ECDH shared secret and salt. */
    fun derivePunchKeys(sharedSecret: ByteArray, salt: ByteArray): PunchSessionKeys = actualPunchDeriveKeys(sharedSecret, salt)

    /** Computes the sender's HMAC proof-of-identity bound to both public keys. */
    fun computeSenderAuthProof(authKey: ByteArray, identityHash: String, senderPubKey: ByteArray, receiverPubKey: ByteArray): ByteArray =
        actualPunchComputeAuthProof(authKey, "sender", identityHash, senderPubKey, receiverPubKey)

    /** Computes the receiver's HMAC proof-of-identity bound to both public keys. */
    fun computeReceiverAuthProof(authKey: ByteArray, identityHash: String, senderPubKey: ByteArray, receiverPubKey: ByteArray): ByteArray =
        actualPunchComputeAuthProof(authKey, "receiver", identityHash, senderPubKey, receiverPubKey)

    /** Verifies an authentication proof in constant time to eliminate timing side-channels. */
    fun verifyAuthProof(expected: ByteArray, actual: ByteArray): Boolean = actualPunchVerifyAuthProof(expected, actual)

    /** Seals frame [seq] of the stream using AES-256-GCM with sequence number bound in AAD. */
    fun sealFrame(sessionKey: ByteArray, seq: Long, plaintext: ByteArray): ByteArray = RelayCrypto.sealFrame(sessionKey, seq, plaintext)

    /** Opens and authenticates sealed frame [seq]. Throws [RelayCryptoException] on any tampering or mismatch. */
    fun openFrame(sessionKey: ByteArray, seq: Long, sealed: ByteArray): ByteArray = RelayCrypto.openFrame(sessionKey, seq, sealed)
}

expect fun actualPunchGenerateKeyPair(): PunchKeyPair

expect fun actualPunchGenerateSalt(): ByteArray

expect fun actualPunchComputeSharedSecret(privateKeyBytes: ByteArray, peerPublicKeyBytes: ByteArray): ByteArray

expect fun actualPunchDeriveKeys(sharedSecret: ByteArray, salt: ByteArray): PunchSessionKeys

expect fun actualPunchComputeAuthProof(authKey: ByteArray, role: String, identityHash: String, senderPubKey: ByteArray, receiverPubKey: ByteArray): ByteArray

expect fun actualPunchVerifyAuthProof(expected: ByteArray, actual: ByteArray): Boolean
