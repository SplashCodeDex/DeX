package com.dexstudios.dex.network

/**
 * Canonical URL paths and route templates of the DeX wire protocol.
 * Values moved verbatim from the previous per-client literals; changing any
 * value breaks the desktop peer and must be a coordinated protocol change.
 */
object ApiRoutes {
    // LocalSend v2 compatible routes
    const val PREPARE_UPLOAD = "/api/localsend/v2/prepare-upload"
    const val UPLOAD = "/api/localsend/v2/upload"
    const val INFO = "/api/localsend/v2/info"

    // DeX-specific routes
    const val CLIPBOARD = "/api/dex/clipboard"
    const val DOWNLOAD = "/download"
    const val PUNCH_ENDPOINT = "/punch/endpoint"
    const val WEBSOCKET = "/ws"

    /** Full HTTPS URL builder for the LocalSend/DeX routes on a given host. */
    fun httpsUrl(ip: String, port: Int, path: String): String = "https://$ip:$port$path"

    /** Full plain-HTTP URL builder (dedicated pull port fallback, no TLS). */
    fun httpUrl(ip: String, port: Int, path: String): String = "http://$ip:$port$path"
}

/**
 * WorkManager input-key vocabulary shared by every writer (UI dispatchers,
 * PendingShareForwarder, TcpDownloadService) and reader (workers). The string
 * values are a persisted contract between enqueue sites and workers; they must
 * never change independently.
 */
object TransferWorkKeys {
    const val IP = "ip"
    const val PORT = "port"
    const val HTTPS_PORT = "httpsPort"
    const val URIS = "uris"
    const val FILES = "files"
    const val TOTAL_BYTES = "totalBytes"
    const val DEST_DIR_URI = "destDirUri"
    const val FOLDER_TREE_URI = "folderTreeUri"
    const val SOURCE_FINGERPRINT = "sourceFingerprint"
    const val SOURCE_ALIAS = "sourceAlias"
    const val TARGET_FINGERPRINT = "targetFingerprint"
    const val TARGET_ALIAS = "targetAlias"
    const val TARGET_IDENTITY_HASH = "targetIdentityHash"
    const val TARGET_GOOGLE_SUB = "targetGoogleSub"
}

/**
 * Broadcast intent actions and extras shared by NotificationHelper,
 * FileTransferReceiver, and the manifest receivers. Moved verbatim.
 */
object TransferIntents {
    const val ACTION_ACCEPT_TRANSFER = "com.dexstudios.dex.ACCEPT_TRANSFER"
    const val ACTION_REJECT_TRANSFER = "com.dexstudios.dex.REJECT_TRANSFER"

    const val EXTRA_SESSION_ID = "SESSION_ID"
    const val EXTRA_NOTIFICATION_ID = "NOTIFICATION_ID"
    const val EXTRA_FINGERPRINT = "FINGERPRINT"
}

/**
 * Operational constants of the network stack, moved verbatim from the
 * previous per-class companions/locals. Tuning values keep their historical
 * numbers — centralizing names them, it does not change behavior.
 */
object NetConfig {
    // Discovery
    const val MULTICAST_GROUP = "224.0.0.167"
    const val NSD_SERVICE_TYPE = "_dex._udp"
    const val NSD_SERVICE_NAME = "DeX_Android"
    const val NSD_DISCOVERY_TTL_MS = 20_000L
    const val DEVICE_CAP = 100
    const val UDP_PRESENCE_CONNECTED_INTERVAL_MS = 30_000L
    const val UDP_PRESENCE_BURST_INTERVAL_MS = 3_000L
    const val UDP_PRESENCE_IDLE_INTERVAL_MS = 10_000L
    const val UDP_BURST_ROUNDS = 3
    const val MANUAL_PROBE_TIMEOUT_MS = 2_000

    // Timeouts (ms)
    const val PAIR_PROMPT_TIMEOUT_MS = 60_000L
    const val GRANT_WAIT_MS = 180_000L
    const val PENDING_SHARE_TIMEOUT_MS = 10L * 60 * 1000

    // Transfer session tuning
    const val MAX_CONCURRENT_TRANSFERS = 3
    const val MAX_RETRY_ATTEMPTS = 3
    const val MAX_FILE_RETRIES = 2
    const val UI_THROTTLE_MS = 200L
    const val SKIP_TOKEN = "[SKIP]"

    // Buffers
    const val STREAM_BUFFER_BYTES = 64 * 1024
    const val UPLOAD_BUFFER_BYTES = 81920
    const val DIRECT_BUFFER_BYTES = 65536

    // Punch session
    const val PUNCH_WINDOW_MS = 12_000L
    const val PUNCH_CONNECT_TIMEOUT_MS = 800
    const val PUNCH_TRANSFER_ATTEMPTS = 3

    // Protocol version and device defaults
    const val PROTOCOL_VERSION = "2.0"
    const val PROTOCOL_HTTPS = "https"
    const val DEVICE_TYPE_MOBILE = "mobile"
    const val DEVICE_TYPE_DESKTOP = "desktop"
}
