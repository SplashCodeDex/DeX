package com.dexstudios.dex.network

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

/** Routing fields stay in WorkManager; URI lists are published before enqueue under the work ID. */
object UploadWorkRequestFactory : KoinComponent {
    private val appContext: Context by inject()
    private fun defaultStore() = UploadManifestStore(appContext.filesDir)

    @WorkerThread
    fun create(
        ip: String,
        port: Int,
        urisJson: String,
        targetFingerprint: String? = null,
        targetAlias: String? = null,
        targetIdentityHash: String? = null,
        targetGoogleSub: String? = null,
        expedited: Boolean = true,
        manifestStore: UploadManifestStore = defaultStore()
    ): OneTimeWorkRequest {
        val data = Data.Builder()
            .putString(TransferWorkKeys.IP, ip)
            .putInt(TransferWorkKeys.PORT, port)
            .putBoolean(TransferWorkKeys.URI_MANIFEST, true)
        if (!targetFingerprint.isNullOrBlank()) data.putString(TransferWorkKeys.TARGET_FINGERPRINT, targetFingerprint)
        if (!targetAlias.isNullOrBlank()) data.putString(TransferWorkKeys.TARGET_ALIAS, targetAlias)
        if (!targetIdentityHash.isNullOrBlank()) data.putString(TransferWorkKeys.TARGET_IDENTITY_HASH, targetIdentityHash)
        if (!targetGoogleSub.isNullOrBlank()) data.putString(TransferWorkKeys.TARGET_GOOGLE_SUB, targetGoogleSub)
        val builder = OneTimeWorkRequestBuilder<UploadWorker>().setInputData(data.build())
        if (expedited) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        return builder.build().also { manifestStore.write(it.id, urisJson) }
    }

    @WorkerThread
    fun create(
        device: DiscoveredDevice,
        urisJson: String,
        expedited: Boolean = true,
        manifestStore: UploadManifestStore = defaultStore()
    ): OneTimeWorkRequest = create(
        device.ip, device.info.port, urisJson, device.info.fingerprint, device.info.alias,
        device.info.identityHash, device.info.googleSub, expedited, manifestStore
    )

    @WorkerThread
    fun createPunch(
        device: DiscoveredDevice,
        urisJson: String,
        expedited: Boolean = true,
        manifestStore: UploadManifestStore = defaultStore()
    ): OneTimeWorkRequest {
        val data = Data.Builder()
            .putString(TransferWorkKeys.TARGET_FINGERPRINT, device.info.fingerprint)
            .putString(TransferWorkKeys.TARGET_ALIAS, device.info.alias)
            .putBoolean(TransferWorkKeys.URI_MANIFEST, true)
            .build()
        val builder = OneTimeWorkRequestBuilder<PunchSendWorker>().setInputData(data)
        if (expedited) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        return builder.build().also { manifestStore.write(it.id, urisJson) }
    }

    /** Returns after durable enqueue; disk and WorkManager waits never run on the UI thread. */
    suspend fun enqueue(
        context: Context,
        device: DiscoveredDevice,
        urisJson: String,
        punch: Boolean = false,
        expedited: Boolean = true
    ): UUID = withContext(Dispatchers.IO) {
        val manager = WorkManager.getInstance(context)
        val store = UploadManifestStore(context.filesDir)
        runCatching { pruneFinished(context) }.onFailure { Timber.w(it, "Manifest cleanup deferred") }
        val request = if (punch) createPunch(device, urisJson, expedited, store) else create(device, urisJson, expedited, store)
        try {
            manager.enqueue(request).result.get()
        } catch (e: Exception) {
            // Preserve a manifest if the enqueue outcome is uncertain.
            runCatching {
                if (manager.getWorkInfoById(request.id).get() == null) store.delete(request.id)
            }.onFailure { Timber.w(it, "Manifest cleanup deferred after enqueue failure") }
            throw e
        }
        request.id
    }

    @WorkerThread
    fun pruneFinished(context: Context) {
        val manager = WorkManager.getInstance(context)
        UploadManifestStore(context.filesDir).pruneFinished {
            manager.getWorkInfoById(it).get()?.state?.isFinished == true
        }
    }
}
