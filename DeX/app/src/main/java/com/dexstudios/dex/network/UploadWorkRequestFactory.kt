package com.dexstudios.dex.network

import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy

/**
 * Factory for creating standardized [OneTimeWorkRequest] instances for [UploadWorker] (Plan 024 Phase 2).
 *
 * Centralizes input data construction and expedited quota policies across the application.
 */
object UploadWorkRequestFactory {

    /**
     * Builds an [UploadWorker] work request using raw endpoint fields.
     */
    fun create(
        ip: String,
        port: Int,
        urisJson: String,
        targetFingerprint: String? = null,
        targetAlias: String? = null,
        targetIdentityHash: String? = null,
        targetGoogleSub: String? = null,
        expedited: Boolean = true
    ): OneTimeWorkRequest {
        val dataBuilder = Data.Builder()
            .putString(TransferWorkKeys.IP, ip)
            .putInt(TransferWorkKeys.PORT, port)
            .putString(TransferWorkKeys.URIS, urisJson)

        if (!targetFingerprint.isNullOrBlank()) {
            dataBuilder.putString(TransferWorkKeys.TARGET_FINGERPRINT, targetFingerprint)
        }
        if (!targetAlias.isNullOrBlank()) {
            dataBuilder.putString(TransferWorkKeys.TARGET_ALIAS, targetAlias)
        }
        if (!targetIdentityHash.isNullOrBlank()) {
            dataBuilder.putString(TransferWorkKeys.TARGET_IDENTITY_HASH, targetIdentityHash)
        }
        if (!targetGoogleSub.isNullOrBlank()) {
            dataBuilder.putString(TransferWorkKeys.TARGET_GOOGLE_SUB, targetGoogleSub)
        }

        val requestBuilder = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(dataBuilder.build())

        if (expedited) {
            requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }

        return requestBuilder.build()
    }

    /**
     * Convenience overload to build an [UploadWorker] work request directly from a [DiscoveredDevice].
     */
    fun create(
        device: DiscoveredDevice,
        urisJson: String,
        expedited: Boolean = true
    ): OneTimeWorkRequest = create(
        ip = device.ip,
        port = device.info.port,
        urisJson = urisJson,
        targetFingerprint = device.info.fingerprint,
        targetAlias = device.info.alias,
        targetIdentityHash = device.info.identityHash,
        targetGoogleSub = device.info.googleSub,
        expedited = expedited
    )
}
