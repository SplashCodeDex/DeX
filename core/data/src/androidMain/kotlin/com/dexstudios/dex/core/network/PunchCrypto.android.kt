package com.dexstudios.dex.core.network

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val EC_CURVE = "secp256r1"

actual fun actualPunchGenerateKeyPair(): PunchKeyPair {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec(EC_CURVE))
    val kp = kpg.generateKeyPair()
    return PunchKeyPair(
        publicKeyBytes = kp.public.encoded,
        privateKeyBytes = kp.private.encoded,
    )
}

actual fun actualPunchGenerateSalt(): ByteArray {
    val salt = ByteArray(PunchCrypto.SALT_LENGTH_BYTES)
    SecureRandom().nextBytes(salt)
    return salt
}

actual fun actualPunchComputeSharedSecret(privateKeyBytes: ByteArray, peerPublicKeyBytes: ByteArray): ByteArray {
    val kf = KeyFactory.getInstance("EC")
    val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
    val pubKey = kf.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))
    val ka = KeyAgreement.getInstance("ECDH")
    ka.init(privKey)
    ka.doPhase(pubKey, true)
    return ka.generateSecret()
}

actual fun actualPunchDeriveKeys(sharedSecret: ByteArray, salt: ByteArray): PunchSessionKeys {
    // HKDF-Extract: PRK = HMAC-SHA256(salt, sharedSecret)
    val extractMac = Mac.getInstance("HmacSHA256")
    extractMac.init(SecretKeySpec(salt, "HmacSHA256"))
    val prk = extractMac.doFinal(sharedSecret)

    // HKDF-Expand for Session Key: T(1) = HMAC-SHA256(PRK, info || 0x01)
    val expandSessionMac = Mac.getInstance("HmacSHA256")
    expandSessionMac.init(SecretKeySpec(prk, "HmacSHA256"))
    expandSessionMac.update(PunchCrypto.PUNCH_SESSION_INFO.toByteArray(Charsets.UTF_8))
    expandSessionMac.update(1.toByte())
    val sessionKey = expandSessionMac.doFinal()
    require(sessionKey.size == PunchCrypto.KEY_LENGTH_BYTES) { "HKDF produced ${sessionKey.size} bytes for session key" }

    // HKDF-Expand for Auth Key: T(1) = HMAC-SHA256(PRK, info || 0x01)
    val expandAuthMac = Mac.getInstance("HmacSHA256")
    expandAuthMac.init(SecretKeySpec(prk, "HmacSHA256"))
    expandAuthMac.update(PunchCrypto.PUNCH_AUTH_INFO.toByteArray(Charsets.UTF_8))
    expandAuthMac.update(1.toByte())
    val authKey = expandAuthMac.doFinal()
    require(authKey.size == PunchCrypto.KEY_LENGTH_BYTES) { "HKDF produced ${authKey.size} bytes for auth key" }

    return PunchSessionKeys(sessionKey = sessionKey, authKey = authKey)
}

actual fun actualPunchComputeAuthProof(authKey: ByteArray, role: String, identityHash: String, senderPubKey: ByteArray, receiverPubKey: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(authKey, "HmacSHA256"))
    mac.update(role.toByteArray(Charsets.UTF_8))
    mac.update(0.toByte())
    mac.update(identityHash.toByteArray(Charsets.UTF_8))
    mac.update(0.toByte())
    mac.update(senderPubKey)
    mac.update(receiverPubKey)
    return mac.doFinal()
}

actual fun actualPunchVerifyAuthProof(expected: ByteArray, actual: ByteArray): Boolean {
    if (expected.size != actual.size) return false
    return MessageDigest.isEqual(expected, actual)
}
