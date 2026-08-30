package com.dexstudios.dex.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
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

        // Notification Channel Groups
        const val GROUP_CONNECTIVITY = "dex_group_connectivity"
        const val GROUP_TRANSFERS = "dex_group_transfers"

        // Notification Channel IDs
        const val CHANNEL_SERVICE = "dex_service_channel_v2"
        const val CHANNEL_PULL = "dex_pull_fg_v2"
        const val CHANNEL_TRANSFERS = "dex_transfers_channel_v2"
        const val CHANNEL_PENDING_SHARE = "dex_share_pending_channel_v2"
        const val CHANNEL_UPLOAD = "upload_channel_v2"
        const val CHANNEL_DOWNLOAD = "download_channel_v2"
        const val CHANNEL_PUNCH = "punch_channel_v2"
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // 1. Clean up legacy channels that had unorganized groups or default badge flags
        val legacyChannels = listOf(
            "dex_service_channel",
            "dex_transfers_channel",
            "dex_share_pending_channel",
            "dex_pull_fg",
            "upload_channel",
            "download_channel",
            "punch_channel"
        )
        for (legacyId in legacyChannels) {
            try {
                manager.deleteNotificationChannel(legacyId)
            } catch (_: Exception) {}
        }

        // 2. Register Notification Channel Groups
        val connectivityGroup = NotificationChannelGroup(
            GROUP_CONNECTIVITY,
            context.getString(R.string.notif_group_connectivity)
        )
        val transfersGroup = NotificationChannelGroup(
            GROUP_TRANSFERS,
            context.getString(R.string.notif_group_transfers)
        )
        manager.createNotificationChannelGroups(listOf(connectivityGroup, transfersGroup))

        // 3. Register Channels with explicit group mappings, importance, descriptions, and badge suppression
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.notif_channel_bg),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_CONNECTIVITY
            description = context.getString(R.string.notif_channel_bg_desc)
            setShowBadge(false)
        }

        val pullChannel = NotificationChannel(
            CHANNEL_PULL,
            context.getString(R.string.notif_channel_pull_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_CONNECTIVITY
            description = context.getString(R.string.notif_channel_pull_desc)
            setShowBadge(false)
        }

        val transfersChannel = NotificationChannel(
            CHANNEL_TRANSFERS,
            context.getString(R.string.notif_channel_transfers_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = GROUP_TRANSFERS
            description = context.getString(R.string.notif_channel_transfers_desc)
            enableVibration(true)
            setShowBadge(false)
        }

        val pendingShareChannel = NotificationChannel(
            CHANNEL_PENDING_SHARE,
            context.getString(R.string.notif_channel_pending_share),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            group = GROUP_TRANSFERS
            description = context.getString(R.string.notif_pending_share_desc)
            setShowBadge(false)
        }

        val uploadChannel = NotificationChannel(
            CHANNEL_UPLOAD,
            context.getString(R.string.notif_channel_upload_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_TRANSFERS
            description = context.getString(R.string.notif_channel_upload_desc)
            setShowBadge(false)
        }

        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOAD,
            context.getString(R.string.notif_channel_download_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_TRANSFERS
            description = context.getString(R.string.notif_channel_download_desc)
            setShowBadge(false)
        }

        val punchChannel = NotificationChannel(
            CHANNEL_PUNCH,
            context.getString(R.string.notif_channel_punch_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_TRANSFERS
            description = context.getString(R.string.notif_channel_punch_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannels(
            listOf(
                serviceChannel,
                pullChannel,
                transfersChannel,
                pendingShareChannel,
                uploadChannel,
                downloadChannel,
                punchChannel
            )
        )
    }

    fun getForegroundServiceNotification(): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(context.getString(R.string.notif_bg_title))
            .setContentText(context.getString(R.string.notif_bg_desc))
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)

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

        val notification = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
            .setContentTitle(context.getString(R.string.notif_incoming_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_incoming_desc, fileCount, fileCount))
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
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

        val notification = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
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

        val notification = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
            .setContentTitle("Transfer Complete")
            .setContentText("Successfully received $fileName")
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
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

        val notification = NotificationCompat.Builder(context, CHANNEL_PENDING_SHARE)
            .setContentTitle(context.getString(R.string.notif_pending_share_title, alias))
            .setContentText(context.getString(R.string.notif_pending_share_desc, alias))
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
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
        val notification = NotificationCompat.Builder(context, CHANNEL_PENDING_SHARE)
            .setContentTitle(context.getString(R.string.notif_pending_share_saved_title))
            .setContentText(context.getString(R.string.notif_pending_share_saved_desc, savedCount, totalCount))
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify("pending_share_saved".hashCode(), notification)
    }

    private fun pendingShareNotificationId(fingerprint: String): Int = "pending_share_$fingerprint".hashCode()
}
