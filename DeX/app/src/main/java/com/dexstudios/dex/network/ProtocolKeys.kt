package com.dexstudios.dex.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Central registry of the DeX WebSocket wire contract.
 *
 * The `type`/`data` envelope and every field name below are protocol law
 * (see docs/PROTOCOL.md): values were moved verbatim from the previous
 * call sites and must never be renamed. If a value changes, the desktop
 * peer must change in the same commit.
 */
object ProtocolKeys {
    // Envelope
    const val TYPE = "type"
    const val DATA = "data"

    // Message types — pairing
    const val PAIR_PROMPT = "pair-prompt"
    const val PAIR_CANCELLED = "pair-cancelled"
    const val PAIR_ACCEPTED = "pair-accepted"
    const val PAIR_REQUEST = "pair-request"
    const val PAIR_RESPONSE = "pair-response"
    const val PIN_DIGIT_ENTERED = "pin-digit-entered"
    const val UNPAIR = "unpair"
    const val TRUST_CHECK = "trust-check"
    const val IDENTITY_CHALLENGE = "identity-challenge"
    const val IDENTITY_PROOF = "identity-proof"

    // Message types — transfers & relay
    const val PREPARE_UPLOAD = "prepare-upload"
    const val RELAY_STARTED = "relay-started"
    const val RELAY_ERROR = "relay-error"
    const val RELAY_TRANSFER = "relay-transfer"
    const val RESOLVE_ENDPOINT = "resolve-endpoint"

    // Message types — discovery & telemetry
    const val DEVICE_ROSTER = "device-roster"
    const val TELEMETRY = "telemetry"
    const val PUBLIC_ADDRESS = "public-address"
    const val ENDPOINT_INFO = "endpoint-info"
    const val PEER_ENDPOINT = "peer-endpoint"

    // Message types — clipboard & wallpaper
    const val SET_CLIPBOARD = "set-clipboard"
    const val WALLPAPER_UPDATED = "wallpaper-updated"

    // Message types — PC File Explorer
    const val LIST_SHARED_FOLDERS = "list-shared-folders"
    const val LIST_SHARED_FOLDERS_REPLY = "list-shared-folders-reply"
    const val BROWSE_FOLDER = "browse-folder"
    const val BROWSE_REPLY = "browse-reply"
    const val PULL_FILES = "pull-files"
    const val PULL_CANCEL = "pull-cancel"
    const val PULL_PROGRESS = "pull-progress"
    const val PULL_REPLY = "pull-reply"
    const val GRANT_SHARED_FOLDER = "grant-shared-folder"
    const val GRANT_REPLY = "grant-reply"

    // Envelope payload field names
    const val ACCEPTED = "accepted"
    const val PIN = "pin"
    const val DIGIT_COUNT = "digitCount"
    const val TOKEN = "token"
    const val FINGERPRINT = "fingerprint"
    const val IS_TRUSTED = "isTrusted"
    const val NONCE = "nonce"
    const val MAC = "mac"
    const val TARGET_FINGERPRINT = "targetFingerprint"
    const val SESSION_ID = "sessionId"
    const val REQUEST_ID = "requestId"
    const val BATTERY = "battery"
    const val WIFI_SSID = "wifiSsid"
    const val WIFI_RSSI = "wifiRssi"

    /** PIN length shared by the pairing UI; mirrors the PC's digit count in the pair prompt. */
    const val PIN_LENGTH = 5

    /**
     * Builds the canonical `{type, data}` envelope as a sendable JSON string.
     * Every outbound WebSocket message must go through this builder so the
     * envelope shape can never drift per call site.
     */
    inline fun envelopeOf(type: String, crossinline data: JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            put(TYPE, type)
            putJsonObject(DATA) { data() }
        }.toString()
}

/** One shared kotlinx Json for protocol decode/encode across the network layer. */
val DexJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Short-lived in-memory result carriers that were previously re-declared as raw
 * buildJsonObject literals at each call site. Shape moved verbatim.
 */
object ProtocolMessages {
    /** `{"type":"pair-request"}` — no data payload. */
    const val PAIR_REQUEST_FRAME = """{"type":"pair-request"}"""

    /** `{"type":"unpair"}` — no data payload. */
    const val UNPAIR_FRAME = """{"type":"unpair"}"""

    /** `{"type":"device-roster","data":{}}` — empty-data roster request. */
    const val DEVICE_ROSTER_FRAME = """{"type":"device-roster","data":{}}"""
}
