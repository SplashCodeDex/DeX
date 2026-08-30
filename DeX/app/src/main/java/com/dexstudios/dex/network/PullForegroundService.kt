package com.dexstudios.dex.network

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dexstudios.dex.R

/**
 * A foreground "holder" that keeps the process alive while the phone is pulling files
 * back to the PC over the WebSocket. Without a running foreground service Android may
 * kill the process (Doze / battery optimization / swipe-away), silently aborting the
 * pull. The actual transfer runs in FileShareManager's scope; this service only holds
 * the foreground guarantee and is stopped when the pull completes or is cancelled.
 */
class PullForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID) ?: ""
        val count = intent?.getIntExtra(EXTRA_COUNT, 0) ?: 0
        startForegroundNotification(requestId, count)
        // Never auto-restart: if we're killed, the pull's coroutine is gone too.
        return START_NOT_STICKY
    }

    private fun startForegroundNotification(requestId: String, count: Int) {
        val title = if (count > 1) "Pulling $count files to PC" else "Pulling file to PC"
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_PULL)
            .setContentTitle(title)
            .setContentText("Keep the app open while files transfer to your PC")
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
            .setOngoing(true)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId(requestId), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notificationId(requestId), notification)
        }
    }

    private fun notificationId(requestId: String) = requestId.hashCode() and 0x7fffffff

    companion object {
        const val EXTRA_REQUEST_ID = "requestId"
        const val EXTRA_COUNT = "count"

        /** Holds the process alive for the duration of a pull. Safe to call on the main thread. */
        fun start(context: Context, requestId: String, count: Int) {
            val intent = Intent(context, PullForegroundService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .putExtra(EXTRA_COUNT, count)
            context.startForegroundService(intent)
        }

        /** Releases the foreground guarantee. No-op if the service was never started. */
        fun stop(context: Context) {
            context.stopService(Intent(context, PullForegroundService::class.java))
        }
    }
}
