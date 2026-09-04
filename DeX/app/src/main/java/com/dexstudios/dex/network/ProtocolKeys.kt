package com.dexstudios.dex.network

import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.core.protocol.MessageTypes
import com.dexstudios.dex.core.protocol.ProtocolEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder

/**
 * Forwarding shim to the canonical shared wire contract leaf module (`:core:protocol`).
 *
 * Per Plan 030 (Android Shared-Core Integration Phase 1), this registry forwards
 * directly to [MessageTypes] and [FieldNames] in `:core:protocol`.
 * Call sites continue to resolve against [ProtocolKeys] without code changes, but
 * the wire contract is now enforced at compile-time by the shared multiplatform core.
 */
object ProtocolKeys {
    // Envelope
    const val TYPE = FieldNames.TYPE
    const val DATA = FieldNames.DATA

    // Message types — pairing
    const val PAIR_PROMPT = MessageTypes.PAIR_PROMPT
    const val PAIR_CANCELLED = MessageTypes.PAIR_CANCELLED
    const val PAIR_ACCEPTED = MessageTypes.PAIR_ACCEPTED
    const val PAIR_REQUEST = MessageTypes.PAIR_REQUEST
    const val PAIR_RESPONSE = MessageTypes.PAIR_RESPONSE
    const val PIN_DIGIT_ENTERED = MessageTypes.PIN_DIGIT_ENTERED
    const val UNPAIR = MessageTypes.UNPAIR
    const val TRUST_CHECK = MessageTypes.TRUST_CHECK
    const val IDENTITY_CHALLENGE = MessageTypes.IDENTITY_CHALLENGE
    const val IDENTITY_PROOF = MessageTypes.IDENTITY_PROOF

    // Message types — transfers & relay
    const val PREPARE_UPLOAD = MessageTypes.PREPARE_UPLOAD
    const val RELAY_STARTED = MessageTypes.RELAY_STARTED
    const val RELAY_ERROR = MessageTypes.RELAY_ERROR
    const val RELAY_TRANSFER = MessageTypes.RELAY_TRANSFER
    const val RELAY_OFFER = MessageTypes.RELAY_OFFER
    const val RESOLVE_ENDPOINT = MessageTypes.RESOLVE_ENDPOINT

    // Message types — discovery & telemetry
    const val DEVICE_ROSTER = MessageTypes.DEVICE_ROSTER
    const val TELEMETRY = MessageTypes.TELEMETRY
    const val PUBLIC_ADDRESS = MessageTypes.PUBLIC_ADDRESS
    const val ENDPOINT_INFO = MessageTypes.ENDPOINT_INFO
    const val PEER_ENDPOINT = MessageTypes.PEER_ENDPOINT

    // Message types — clipboard & wallpaper
    const val SET_CLIPBOARD = MessageTypes.SET_CLIPBOARD
    const val WALLPAPER_UPDATED = MessageTypes.WALLPAPER_UPDATED

    // Message types — PC File Explorer
    const val LIST_SHARED_FOLDERS = MessageTypes.LIST_SHARED_FOLDERS
    const val LIST_SHARED_FOLDERS_REPLY = MessageTypes.LIST_SHARED_FOLDERS_REPLY
    const val BROWSE_FOLDER = MessageTypes.BROWSE_FOLDER
    const val BROWSE_REPLY = MessageTypes.BROWSE_REPLY
    const val PULL_FILES = MessageTypes.PULL_FILES
    const val PULL_CANCEL = MessageTypes.PULL_CANCEL
    const val PULL_PROGRESS = MessageTypes.PULL_PROGRESS
    const val PULL_REPLY = MessageTypes.PULL_REPLY
    const val GRANT_SHARED_FOLDER = MessageTypes.GRANT_SHARED_FOLDER
    const val GRANT_REPLY = MessageTypes.GRANT_REPLY

    // Envelope payload field names
    const val ACCEPTED = FieldNames.ACCEPTED
    const val PIN = FieldNames.PIN
    const val DIGIT_COUNT = FieldNames.DIGIT_COUNT
    const val TOKEN = FieldNames.TOKEN
    const val FINGERPRINT = FieldNames.FINGERPRINT
    const val IS_TRUSTED = FieldNames.IS_TRUSTED
    const val NONCE = FieldNames.NONCE
    const val MAC = FieldNames.MAC
    const val TARGET_FINGERPRINT = FieldNames.TARGET_FINGERPRINT
    const val SESSION_ID = FieldNames.SESSION_ID
    const val REQUEST_ID = FieldNames.REQUEST_ID
    const val STREAM_TOKEN = FieldNames.STREAM_TOKEN
    const val RELAY_URL = FieldNames.RELAY_URL
    const val FILE_NAME = FieldNames.FILE_NAME
    const val SIZE = FieldNames.SIZE
    const val BATTERY = FieldNames.BATTERY
    const val WIFI_SSID = FieldNames.WIFI_SSID
    const val WIFI_RSSI = FieldNames.WIFI_RSSI

    /** PIN length shared by the pairing UI; mirrors the PC's digit count in the pair prompt. */
    const val PIN_LENGTH = com.dexstudios.dex.core.domain.pairing.PairingEngine.PIN_LENGTH

    /**
     * Builds the canonical `{type, data}` envelope as a sendable JSON string.
     * Delegates directly to [ProtocolEnvelope.envelopeOf].
     */
    inline fun envelopeOf(type: String, crossinline data: JsonObjectBuilder.() -> Unit = {}): String =
        ProtocolEnvelope.envelopeOf(type, data)
}

/** One shared kotlinx Json for protocol decode/encode across the network layer. */
val DexJson: Json = ProtocolEnvelope.json

/**
 * Short-lived in-memory result carriers that were previously re-declared as raw
 * buildJsonObject literals at each call site. Shape moved verbatim.
 */
object ProtocolMessages {
    /** `{"type":"pair-request"}` — no data payload. */
    const val PAIR_REQUEST_FRAME = """{"type":"${MessageTypes.PAIR_REQUEST}"}"""

    /** `{"type":"unpair"}` — no data payload. */
    const val UNPAIR_FRAME = """{"type":"${MessageTypes.UNPAIR}"}"""

    /** `{"type":"device-roster","data":{}}` — empty-data roster request. */
    val DEVICE_ROSTER_FRAME: String = ProtocolEnvelope.envelopeOf(MessageTypes.DEVICE_ROSTER)
}
