package com.dexstudios.dex.network

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.dexstudios.dex.R
import java.util.UUID

/**
 * Centralized delegate for constructing WorkManager transfer foreground notifications (Plan 024 Phase 3).
 *
 * Ensures consistent notification channels, icons, progress bars, cancellation intents,
 * and API 34+ [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC] compliance across all transfer workers.
 */
object TransferProgressNotifier {

    /**
     * Builds a standardized [ForegroundInfo] instance for an active transfer worker.
     *
     * @param context Application context.
     * @param workId Unique UUID of the active WorkRequest for binding cancellation.
     * @param title Header title of the notification (e.g. "Sending Files", "Receiving File").
     * @param text Subtitle description with current file, percent, speed, and ETA.
     * @param progress Progress percentage [0..100].
     * @param notificationId Stable integer ID for the foreground notification.
     * @param channelId Target notification channel ID (defaults to [NotificationHelper.CHANNEL_TRANSFER]).
     */
    fun createForegroundInfo(
        context: Context,
        workId: UUID,
        title: String,
        text: String,
        progress: Int,
        notificationId: Int = 1001,
        channelId: String = NotificationHelper.CHANNEL_TRANSFERS
    ): ForegroundInfo {
        val cancelIntent = runCatching { WorkManager.getInstance(context).createCancelPendingIntent(workId) }.getOrNull()

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setOngoing(true)

        if (cancelIntent != null) {
            builder.addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
        }

        val notification = runCatching { builder.build() }.getOrNull() ?: android.app.Notification()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
