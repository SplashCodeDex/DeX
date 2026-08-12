package com.dexstudios.dex.network

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import org.koin.android.ext.android.inject

class DexService : Service() {
    private val webSocketClientService: WebSocketClientService by inject()
    private val discoveryEngine: DiscoveryEngine by inject()
    private val notificationHelper: NotificationHelper by inject()
    private val punchSession: PunchSession by inject()
    private val clipboardSyncManager: ClipboardSyncManager by inject()

    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, 1, notificationHelper.getForegroundServiceNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, notificationHelper.getForegroundServiceNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notificationHelper.getForegroundServiceNotification())
        }

        // Acquire Multicast lock to ensure UDP broadcasts are received
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager?.createMulticastLock("DexMulticastLock")
        multicastLock?.setReferenceCounted(true)
        multicastLock?.acquire()

        webSocketClientService.start()
        discoveryEngine.startDiscovery()
        // Listen for direct (NAT-punched) transfers from same-email devices
        punchSession.start()
        // Auto-push clipboard changes to the connected PC (2-way sync)
        clipboardSyncManager.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardSyncManager.stop()
        webSocketClientService.stop()
        discoveryEngine.stopDiscovery()
        punchSession.stop()

        multicastLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ dataSync FGS 6-hour limit: stop gracefully to avoid ForegroundServiceDidNotStopInTimeException
        if (fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC != 0) {
            stopSelf(startId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        @Volatile
        private var instance: DexService? = null

        /**
         * Re-declares the foreground service type. Android 14+ requires the service to
         * be running with the MEDIA_PROJECTION type while a mirror session is active.
         */
        fun setMirroring(mirroring: Boolean) {
            val svc = instance ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        if (mirroring) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
                ServiceCompat.startForeground(svc, 1, svc.notificationHelper.getForegroundServiceNotification(), types)
            } else {
                svc.startForeground(1, svc.notificationHelper.getForegroundServiceNotification())
            }
        }
    }
}
