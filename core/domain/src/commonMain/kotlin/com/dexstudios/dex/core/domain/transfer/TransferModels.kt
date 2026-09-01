package com.dexstudios.dex.core.domain.transfer

/**
 * Live state of ONE transfer session. The shape mirrors the legacy
 * TransferStateMonitor entry exactly so the desktop dashboard renders unchanged —
 * field-for-field, semantic-for-semantic.
 */
data class TransferSession(
    val sessionId: String,
    val senderAlias: String,
    val totalFiles: Int,
    val filesReceived: Int = 0,
    val isComplete: Boolean = false,
    val bytesReceived: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    val etaSeconds: Long? = null,
    val currentFileName: String = "",
)

/**
 * Domain model of one offered file. Mapped FROM the wire DTO
 * (PrepareUploadRequestDto.files / PullFileDto) at the network boundary — the domain
 * never sees transport types.
 */
data class TransferFile(
    val id: String,
    val fileName: String,
    val size: Long,
    /** Pull credential minted by the sender for hosted downloads; null when N/A. */
    val token: String? = null,
    /** Destination sub-path when the offer carries a folder structure. */
    val relativePath: String? = null,
)

/**
 * A complete transfer offer as the domain understands it.
 *
 * [sessionId] is the receiver-side identifier for the session this offer belongs to
 * (inbound offers create one; outbound pulls mint one at request time).
 */
data class TransferOffer(
    val sessionId: String,
    val senderAlias: String,
    val senderFingerprint: String,
    /** Where to fetch the bytes from (peer https port / tcp fallback port). */
    val senderAddress: String,
    val senderPort: Int,
    val tcpFallbackPort: Int,
    val files: List<TransferFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.size }
}

/** Terminal persistence verdict handed to the history port when a file settles. */
enum class TransferOutcome {
    COMPLETED,
    FAILED,
}

/** Domain-facing result of reporting live progress (values preserved verbatim from the legacy monitor). */
data class TransferProgress(
    val filesDone: Int,
    val totalFiles: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBps: Long = 0L,
    val etaSeconds: Long? = null,
    val currentFileName: String = "",
)
