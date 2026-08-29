package com.dexstudios.dex.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.dexstudios.dex.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val ACTION_STOP_MIRRORING = "com.dexstudios.dex.STOP_MIRRORING"
        const val ACTION_CANCEL_PENDING_SHARE = "com.dexstudios.dex.CANCEL_PENDING_SHARE"
    }

    private val serviceChannelId = "dex_service_channel"
    private val transfersChannelId = "dex_transfers_channel"
    private val pendingShareChannelId = "dex_share_pending_channel"

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val serviceChannel = NotificationChannel(
            serviceChannelId,
            context.getString(R.string.notif_channel_bg),
            NotificationManager.IMPORTANCE_LOW
        )
        val transfersChannel = NotificationChannel(
            transfersChannelId,
            "DeX Transfers & Pairing",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming transfers, pairing requests, and transfer completion"
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(transfersChannel)
        manager.createNotificationChannel(
            NotificationChannel(
                pendingShareChannelId,
                context.getString(R.string.notif_channel_pending_share),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    fun getForegroundServiceNotification(): Notification {
        val builder = NotificationCompat.Builder(context, serviceChannelId)
            .setContentTitle(context.getString(R.string.notif_bg_title))
            .setContentText(context.getString(R.string.notif_bg_desc))
            .setSmallIcon(R.drawable.ic_stat_dex)

        if (MirrorSession.active) {
            val stopIntent = Intent(context, FileTransferReceiver::class.java).apply {
                action = ACTION_STOP_MIRRORING
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context, 10, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Mirroring", stopPendingIntent)
        }

        return builder.build()
    }

    fun showIncomingFileNotification(sessionId: String, notificationId: Int, fileCount: Int) {
        val acceptIntent = Intent(context, FileTransferReceiver::class.java).apply {
            action = "com.dexstudios.dex.ACCEPT_TRANSFER"
            putExtra("SESSION_ID", sessionId)
            putExtra("NOTIFICATION_ID", notificationId)
        }

        val rejectIntent = Intent(context, FileTransferReceiver::class.java).apply {
            action = "com.dexstudios.dex.REJECT_TRANSFER"
            putExtra("SESSION_ID", sessionId)
            putExtra("NOTIFICATION_ID", notificationId)
        }

        val acceptPendingIntent = PendingIntent.getBroadcast(
            context, notificationId, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 1, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, transfersChannelId)
            .setContentTitle(context.getString(R.string.notif_incoming_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_incoming_desc, fileCount, fileCount))
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(android.R.drawable.ic_menu_add, context.getString(R.string.notif_action_accept), acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.notif_action_reject), rejectPendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
    }

    fun showPairingRequestNotification(alias: String) {
        val intent = Intent(context, com.dexstudios.dex.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteViews = android.widget.RemoteViews(context.packageName, R.layout.notification_pairing).apply {
            setTextViewText(R.id.notification_device_name, alias)
        }

        val notification = NotificationCompat.Builder(context, transfersChannelId)
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify("pairing".hashCode(), notification)
    }

    /** Removes the pending pairing-request notification (e.g. when the PC cancels the attempt). */
    fun cancelPairingNotification() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel("pairing".hashCode())
    }

    fun showTransferCompleteNotification(fileName: String, uri: Uri) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, fileName.hashCode(), openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, transfersChannelId)
            .setContentTitle("Transfer Complete")
            .setContentText("Successfully received $fileName")
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open", openPendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(fileName.hashCode(), notification)
    }

    /**
     * Ongoing "waiting for the PC" notice for a queued Direct Share. Silent-ish by
     * channel (IMPORTANCE_DEFAULT — no vibration); the user can cancel the wait
     * from the shade.
     */
    fun showPendingShareNotification(alias: String, fingerprint: String) {
        val cancelIntent = Intent(context, FileTransferReceiver::class.java).apply {
            action = ACTION_CANCEL_PENDING_SHARE
            putExtra("FINGERPRINT", fingerprint)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, fingerprint.hashCode(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, pendingShareChannelId)
            .setContentTitle(context.getString(R.string.notif_pending_share_title, alias))
            .setContentText(context.getString(R.string.notif_pending_share_desc, alias))
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.cancel), cancelPendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(pendingShareNotificationId(fingerprint), notification)
    }

    fun cancelPendingShareNotification(fingerprint: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(pendingShareNotificationId(fingerprint))
    }

    /** Terminal notice when a queued share expired without the PC ever appearing. */
    fun showPendingShareSavedNotification(savedCount: Int, totalCount: Int) {
        val notification = NotificationCompat.Builder(context, pendingShareChannelId)
            .setContentTitle(context.getString(R.string.notif_pending_share_saved_title))
            .setContentText(context.getString(R.string.notif_pending_share_saved_desc, savedCount, totalCount))
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify("pending_share_saved".hashCode(), notification)
    }

    private fun pendingShareNotificationId(fingerprint: String): Int = "pending_share_$fingerprint".hashCode()
}
