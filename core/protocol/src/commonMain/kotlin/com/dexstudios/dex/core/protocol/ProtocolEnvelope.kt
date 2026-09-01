package com.dexstudios.dex.core.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The canonical `{type, data}` envelope every control-channel message rides in.
 * Build with [ProtocolEnvelope.envelopeOf], decode with [ProtocolEnvelope.decodeType]
 * / [ProtocolEnvelope.decodeData]. No call site may hand-roll the envelope shape.
 */
object ProtocolEnvelope {
    /** One shared Json for protocol decode/encode; unknown fields must never break peers. */
    val json: Json = Json { ignoreUnknownKeys = true }

    /** Builds the canonical `{type, data}` envelope as a sendable JSON string. */
    inline fun envelopeOf(type: String, crossinline data: JsonObjectBuilder.() -> Unit = {}): String = buildJsonObject {
        put(FieldNames.TYPE, type)
        putJsonObject(FieldNames.DATA) { data() }
    }.toString()

    /** Builds the envelope as a structured [JsonObject] for senders that serialize later. */
    inline fun envelopeObjectOf(type: String, crossinline data: JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
        put(FieldNames.TYPE, type)
        putJsonObject(FieldNames.DATA) { data() }
    }

    /** Extracts the `type` discriminator, or null when absent/malformed. */
    fun decodeType(frame: String): String? = runCatching {
        frame.parseJson().jsonObject[FieldNames.TYPE]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** Extracts the `data` object, or null when absent/malformed. */
    fun decodeData(frame: String): JsonObject? = runCatching {
        frame.parseJson().jsonObject[FieldNames.DATA] as? JsonObject
    }.getOrNull()

    private fun String.parseJson(): JsonObject = json.parseToJsonElement(this).jsonObject
}
