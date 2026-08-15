package com.dexstudios.dex.network.protocol

object HashUtils {
    fun sha256(input: String): String {
        // Multiplatform SHA-256 (for now using kotlinx or common expected behavior)
        // Since we don't have a KMP crypto library installed yet, we can use expect/actual or simple string hash for now
        // But for commonMain, we should expect this.
        return actualSha256(input)
    }

    fun generateUUID(): String = actualGenerateUUID()
    
    fun currentTimeMillis(): Long = actualCurrentTimeMillis()
}

expect fun actualSha256(input: String): String
expect fun actualGenerateUUID(): String
expect fun actualCurrentTimeMillis(): Long
