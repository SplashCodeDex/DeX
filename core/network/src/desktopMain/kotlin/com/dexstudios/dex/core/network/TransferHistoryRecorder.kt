package com.dexstudios.dex.core.network

import com.dexstudios.dex.core.domain.transfer.TransferOutcome
import com.dexstudios.dex.core.domain.transfer.TransferUseCase

/**
 * Desktop adapter for the domain history port (plan 027): maps domain outcomes onto the
 * TransferHistory entry contract — exactly the call shape the routes and pull service
 * used inline before the extraction. COMPLETED -> status "success"; FAILED -> "failed".
 */
object TransferHistoryRecorder {
    fun record(name: String, size: Long, direction: String, uri: String?, peerDevice: String?, outcome: TransferOutcome) {
        TransferHistory.log(
            name = name,
            size = size,
            direction = direction,
            uri = uri,
            peerDevice = peerDevice,
            status = when (outcome) {
                TransferOutcome.COMPLETED -> TransferUseCase.STATUS_SUCCESS
                TransferOutcome.FAILED -> TransferUseCase.STATUS_FAILED
            },
        )
    }

    /** Convenience for the common success path with a resolved destination. */
    fun recordCompleted(name: String, size: Long, direction: String, uri: String?, peerDevice: String?) {
        record(name, size, direction, uri, peerDevice, TransferOutcome.COMPLETED)
    }

    /** Convenience for the failed path (no landing uri). */
    fun recordFailed(name: String, size: Long, direction: String, peerDevice: String?) {
        record(name, size, direction, uri = null, peerDevice = peerDevice, outcome = TransferOutcome.FAILED)
    }
}
