package com.dexstudios.dex.network

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Keeps the phone reachable for PC pushes in the background: restarts the foreground
 * service (and thus the WebSocket) when the OS kills the process, but only within
 * [KEEP_ALIVE_WINDOW_MS] of the last time the user opened the app. After the window
 * closes, the background service is stopped to save battery.
 */
class KeepAliveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val wsService: WebSocketClientService by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastActive = prefs.getLong(KEY_LAST_ACTIVE, 0L)

        if (System.currentTimeMillis() - lastActive > KEEP_ALIVE_WINDOW_MS) {
            // Window closed: stop maintaining the connection to save battery
            applicationContext.stopService(Intent(applicationContext, DexService::class.java))
            Timber.i("Keep-alive window expired; background service stopped")
            return@withContext Result.success()
        }

        // Restart the foreground service if the OS killed the process. startForegroundService
        // is a no-op when it is already running; WorkManager grants a temporary allowlist.
        runCatching {
            applicationContext.startForegroundService(Intent(applicationContext, DexService::class.java))
        }.onFailure { Timber.e(it, "Keep-alive: cannot restart DexService") }

        // Reconnect the WebSocket to the remembered PC if it dropped while the process lived on
        wsService.ensureConnected()

        Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "dex_keepalive"
        const val KEEP_ALIVE_WINDOW_MS = 6L * 60 * 60 * 1000
        const val PREFS = "dex_keepalive_prefs"
        const val KEY_LAST_ACTIVE = "last_active_ts"
    }
}
