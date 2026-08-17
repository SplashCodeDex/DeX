package com.dexstudios.dex.core.network

import java.security.MessageDigest
import java.util.UUID

actual fun actualSha256(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

actual fun actualGenerateUUID(): String {
    return UUID.randomUUID().toString()
}

actual fun actualCurrentTimeMillis(): Long {
    return System.currentTimeMillis()
}
