package com.dexstudios.dex.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Queue-and-send-when-online for Direct Share taps on an offline PC (the AirDrop
 * promise): the share stays alive behind an ongoing notification and fires the
 * moment the target PC appears in discovery. The trampoline's bounded 6-second
 * wait covers the fast path; this forwarder covers everything slower.
 *
 * Lifetime honesty: the queued content URIs are share-intent grants that die with
 * the process, so the queue is in-memory and the process is kept alive (and
 * discovering) by [DexService]'s foreground service for as long as a share waits.
 * If the OS kills the process anyway, the queue dies with it — nothing pretends
 * to be persisted. When the wait expires without the PC ever appearing, the files
 * fall back to the sandbox save so data is never silently dropped.
 */
object PendingShareForwarder : KoinComponent {

    private const val PENDING_SHARE_TIMEOUT_MS = NetConfig.PENDING_SHARE_TIMEOUT_MS

    private data class PendingShare(
        val fingerprint: String,
        val alias: String,
        val uris: List<Uri>,
        val enqueuedAtMs: Long
    )

    private val pending = ConcurrentHashMap<String, PendingShare>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val discoveryEngine: DiscoveryEngine by inject()
    private val clientEngine: ClientEngine by inject()
    private val notificationHelper: NotificationHelper by inject()
    private var appContext: Context? = null

    fun enqueue(context: Context, fingerprint: String, alias: String, uris: List<Uri>) {
        val appContext = context.applicationContext
        this.appContext = appContext

        val item = PendingShare(fingerprint, alias, uris, System.currentTimeMillis())
        pending[fingerprint] = item

        // Keep the process (and the URI grants inside it) alive — and discovery
        // running — while the share waits for its target.
        runCatching {
            appContext.startForegroundService(Intent(appContext, DexService::class.java))
        }.onFailure { Timber.e(it, "PendingShare: cannot start DexService") }

        notificationHelper.showPendingShareNotification(alias, fingerprint)

        scope.launch {
            val device = withTimeoutOrNull(PENDING_SHARE_TIMEOUT_MS) {
                discoveryEngine.devices
                    .firstOrNull { it.containsKey(fingerprint) }
                    ?.getValue(fingerprint)
            }
            // Only act if this entry is still the live one (not cancelled or replaced)
            if (pending.remove(fingerprint) != item) return@launch
            notificationHelper.cancelPendingShareNotification(fingerprint)

            if (device != null) {
                Timber.i("PendingShare: PC '%s' came online; dispatching %d file(s)", alias, uris.size)
                send(item, device)
            } else {
                Timber.i("PendingShare: PC '%s' never appeared; falling back to sandbox save", alias)
                val saved = SafStorage.saveUrisToSandbox(appContext, uris)
                notificationHelper.showPendingShareSavedNotification(saved, uris.size)
            }
        }
    }

    fun cancel(fingerprint: String) {
        pending.remove(fingerprint)
        notificationHelper.cancelPendingShareNotification(fingerprint)
    }

    private fun send(item: PendingShare, device: DiscoveredDevice) {
        val context = appContext ?: return
        clientEngine.resetUploadState()

        val urisJson = try {
            Json.encodeToString(item.uris.map { it.toString() })
        } catch (e: Exception) {
            Timber.e(e, "PendingShare: cannot serialize URIs")
            return
        }

        val inputData = workDataOf(
            TransferWorkKeys.IP to device.ip,
            TransferWorkKeys.PORT to device.info.port,
            TransferWorkKeys.URIS to urisJson,
            TransferWorkKeys.TARGET_FINGERPRINT to device.info.fingerprint,
            TransferWorkKeys.TARGET_ALIAS to device.info.alias
        )

        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        clientEngine.activeWorkId = workRequest.id
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}