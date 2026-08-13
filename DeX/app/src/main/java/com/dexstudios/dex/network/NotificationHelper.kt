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
    }

    private val channelId = "dex_service_channel"

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notif_channel_bg),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    fun getForegroundServiceNotification(): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
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

        val notification = NotificationCompat.Builder(context, channelId)
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

        val notification = NotificationCompat.Builder(context, channelId)
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

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Transfer Complete")
            .setContentText("Successfully received $fileName")
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open", openPendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(fileName.hashCode(), notification)
    }
}
