package com.dexstudios.dex.network.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object TokenCodec {
    fun encode(map: Map<String, String>): String {
        return Json.encodeToString(map)
    }

    fun decode(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return try {
            Json.decodeFromString<Map<String, String>>(raw)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
