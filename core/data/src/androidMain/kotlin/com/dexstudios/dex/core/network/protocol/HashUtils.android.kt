package com.dexstudios.dex.core.network

import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// HashUtils Android actuals: java.security / javax.crypto standard implementations.

actual fun actualSha256(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

actual fun actualGenerateUUID(): String = UUID.randomUUID().toString()

actual fun actualCurrentTimeMillis(): Long = System.currentTimeMillis()

actual fun actualHmacSha256Base64(secret: String, data: ByteArray): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    return Base64.getEncoder().encodeToString(mac.doFinal(data))
}
