package com.dexstudios.dex.core.protocol

/**
 * DeX wire-contract message types — the single source of truth for every `type` field
 * on the control channel (see docs/PROTOCOL.md).
 *
 * Values are protocol law: a rename here breaks every peer, so desktop, phone, watch,
 * tablet and the relay server must all change in the same release. New message types
 * get a new constant — existing constants are append-only.
 *
 * `*-reply` constants carry request-scoped answers over the WebSocket; replies resolve
 * via `requestId` through DexRequestStore on the desktop side.
 */
object MessageTypes {
    // ---- Pairing ----
    const val PAIR_REQUEST = "pair-request"
    const val PAIR_PROMPT = "pair-prompt"
    const val PAIR_RESPONSE = "pair-response"
    const val PAIR_ACCEPTED = "pair-accepted"
    const val PAIR_CANCELLED = "pair-cancelled"
    const val PIN_DIGIT_ENTERED = "pin-digit-entered"
    const val UNPAIR = "unpair"

    // ---- Trust & identity ----
    const val IDENTITY_CHALLENGE = "identity-challenge"
    const val IDENTITY_PROOF = "identity-proof"
    const val TRUST_CHECK = "trust-check"

    // ---- Transfers & relay ----
    const val PREPARE_UPLOAD = "prepare-upload"
    const val RELAY_TRANSFER = "relay-transfer"
    const val RELAY_STARTED = "relay-started"
    const val RELAY_ERROR = "relay-error"
    const val RELAY_OFFER = "relay-offer"

    // ---- NAT punch rendezvous ----
    const val RESOLVE_ENDPOINT = "resolve-endpoint"
    const val ENDPOINT_INFO = "endpoint-info"
    const val PEER_ENDPOINT = "peer-endpoint"

    // ---- Discovery, roster & telemetry ----
    const val DEVICE_ROSTER = "device-roster"
    const val PUBLIC_ADDRESS = "public-address"
    const val TELEMETRY = "telemetry"

    // ---- Clipboard & wallpaper ----
    const val SET_CLIPBOARD = "set-clipboard"
    const val WALLPAPER_UPDATED = "wallpaper-updated"

    // ---- Screen mirroring ----
    const val MIRROR_START = "mirror-start"
    const val MIRROR_STOP = "mirror-stop"
    const val MIRROR_CONFIG = "mirror-config"

    // ---- File explorer (phone shared folders browsed by the PC) ----
    const val LIST_SHARED_FOLDERS = "list-shared-folders"
    const val LIST_SHARED_FOLDERS_REPLY = "list-shared-folders-reply"
    const val BROWSE_FOLDER = "browse-folder"
    const val BROWSE_REPLY = "browse-reply"
    const val PULL_FILES = "pull-files"
    const val PULL_CANCEL = "pull-cancel"
    const val PULL_PROGRESS = "pull-progress"
    const val PULL_REPLY = "pull-reply"
    const val GRANT_SHARED_FOLDER = "grant-shared-folder"
    const val GRANT_SHARED_FOLDER_REPLY = "grant-shared-folder-reply"
    const val GRANT_REPLY = "grant-reply"

    /** Generic request-scoped reply carrier used by several explorer flows. */
    const val REPLY = "reply"
}

/**
 * Canonical envelope + payload field names. The `count` vs `digitCount` mismatch bug
 * (see docs/PROTOCOL.md) is the exact class of drift these constants exist to kill:
 * field names here must never be restated as string literals at call sites.
 */
object FieldNames {
    const val TYPE = "type"
    const val DATA = "data"

    // Envelope payload fields — pairing & identity
    const val ACCEPTED = "accepted"
    const val PIN = "pin"
    const val ALIAS = "alias"
    const val DIGIT_COUNT = "digitCount"
    const val TOKEN = "token"
    const val FINGERPRINT = "fingerprint"
    const val IS_TRUSTED = "isTrusted"
    const val NONCE = "nonce"
    const val MAC = "mac"

    // Transfers & relay
    const val TARGET_FINGERPRINT = "targetFingerprint"
    const val SESSION_ID = "sessionId"
    const val REQUEST_ID = "requestId"
    const val STREAM_TOKEN = "streamToken"
    const val RELAY_URL = "relayUrl"
    const val FILE_NAME = "fileName"
    const val SIZE = "size"

    // Telemetry
    const val BATTERY = "battery"
    const val WIFI_SSID = "wifiSsid"
    const val WIFI_RSSI = "wifiRssi"

    // Telemetry / public address
    const val ADDRESS = "address"
    const val IS_CHARGING = "isCharging"

    // Pull progress
    const val DONE_FILES = "doneFiles"
    const val TOTAL_FILES = "totalFiles"
    const val SENT_BYTES = "sentBytes"
    const val TOTAL_BYTES = "totalBytes"
    const val CURRENT_FILE = "currentFile"
    const val STATE = "state"
    const val STATE_DONE = "done"
    const val STATE_CANCELLED = "cancelled"
    const val STATE_FAILED = "failed"
    const val STATE_RUNNING = "running"

    // Clipboard
    const val TEXT = "text"
    const val IMAGE_BASE64 = "imageBase64"

    // Mirror config
    const val WIDTH = "width"
    const val HEIGHT = "height"
    const val FPS = "fps"
}
