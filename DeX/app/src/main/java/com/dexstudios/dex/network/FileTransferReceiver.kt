package com.dexstudios.dex.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

// Removed TransferPromptState object

class FileTransferReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == NotificationHelper.ACTION_STOP_MIRRORING) {
            MirrorSession.stop()
            return
        }

        if (action == NotificationHelper.ACTION_CANCEL_PENDING_SHARE) {
            val fingerprint = intent.getStringExtra("FINGERPRINT") ?: return
            PendingShareForwarder.cancel(fingerprint)
            return
        }

        val sessionId = intent.getStringExtra("SESSION_ID") ?: return

        TransferState.pendingPrompts.remove(sessionId)?.complete(action == "com.dexstudios.dex.ACCEPT_TRANSFER")

        // Cancel notification
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", 0)
        if (notificationId != 0) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }
}
