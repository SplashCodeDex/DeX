package com.dexstudios.dex.core.network

object HashUtils {
    fun sha256(input: String): String {
        // Multiplatform SHA-256 (for now using kotlinx or common expected behavior)
        // Since we don't have a KMP crypto library installed yet, we can use expect/actual or simple string hash for now
        // But for commonMain, we should expect this.
        return actualSha256(input)
    }

    fun generateUUID(): String = actualGenerateUUID()

    fun currentTimeMillis(): Long = actualCurrentTimeMillis()

    /** HMAC-SHA256 of [data] keyed by [secret], Base64-encoded — proof-of-possession primitive. */
    fun hmacSha256Base64(secret: String, data: ByteArray): String = actualHmacSha256Base64(secret, data)
}

expect fun actualSha256(input: String): String
expect fun actualGenerateUUID(): String
expect fun actualCurrentTimeMillis(): Long
expect fun actualHmacSha256Base64(secret: String, data: ByteArray): String
