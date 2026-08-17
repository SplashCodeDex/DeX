package com.dexstudios.dex.core.network

import android.app.Notification

interface INotificationHelper {
    fun getForegroundServiceNotification(): Notification
    fun showIncomingFileNotification(sessionId: String, notificationId: Int, fileCount: Int)
    fun showPairingRequestNotification(alias: String)
    fun cancelPairingNotification()
    fun showTransferCompleteNotification(fileName: String, uri: android.net.Uri)
}
