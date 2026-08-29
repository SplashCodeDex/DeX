package com.dexstudios.dex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dexstudios.dex.network.AuthState
import com.dexstudios.dex.network.DexService
import com.dexstudios.dex.network.KeepAliveWorker
import timber.log.Timber

/**
 * Utility-app step 1: after a reboot the phone rejoins the mesh without the user
 * opening the app, so PC pushes and Direct Share keep working.
 *
 * Deliberately respects the existing battery contract: the service only auto-starts
 * when (a) at least one PC is paired — there is nothing to receive otherwise — and
 * (b) the boot happened inside the same keep-alive window (the 6h span since the
 * user last opened the app) that already governs [KeepAliveWorker]'s background
 * restarts. Outside that window the service stays off until the user opens the app
 * again. DeviceManager state is already hydrated: Application.onCreate runs before
 * any receiver delivery in the same process.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != ACTION_QUICKBOOT_POWERON) return

        if (AuthState.pairedFingerprints.isEmpty()) {
            Timber.i("Boot: no paired PCs; skipping service auto-start")
            return
        }

        val prefs = context.getSharedPreferences(KeepAliveWorker.PREFS, Context.MODE_PRIVATE)
        val lastActive = prefs.getLong(KeepAliveWorker.KEY_LAST_ACTIVE, 0L)
        if (System.currentTimeMillis() - lastActive > KeepAliveWorker.KEEP_ALIVE_WINDOW_MS) {
            Timber.i("Boot: keep-alive window expired; skipping service auto-start")
            return
        }

        runCatching {
            context.startForegroundService(Intent(context, DexService::class.java))
            Timber.i("Boot: DexService started")
        }.onFailure { Timber.e(it, "Boot: cannot start DexService") }
    }

    companion object {
        // OEM-specific power-on broadcast (HTC and some others) mirroring BOOT_COMPLETED
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}