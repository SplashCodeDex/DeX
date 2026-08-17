package com.dexstudios.dex.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dexstudios.dex.core.network.DeviceConfig

actual class SettingsPlatformHelper(private val context: Context) {
    actual val appVersion: String = "Android 2.0.0"

    actual fun signInWithGoogle(deviceConfig: DeviceConfig) {
        val activity = context as? android.app.Activity
        if (activity != null) {
            Toast.makeText(context, "Sign in not available", Toast.LENGTH_SHORT).show()
        }
    }

    actual fun getDeviceName(): String {
        return "Android Device"
    }

    actual fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    actual fun addQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sbm = context.getSystemService("statusbar")
            try {
                val method = sbm?.javaClass?.getMethod(
                    "requestAddTileService",
                    android.content.ComponentName::class.java,
                    CharSequence::class.java,
                    android.graphics.drawable.Icon::class.java,
                    java.util.concurrent.Executor::class.java,
                    java.util.function.Consumer::class.java
                )
                val componentName = android.content.ComponentName(
                    context,
                    Class.forName("com.dexstudios.dex.network.ClipboardSyncTileService")
                )
                val icon = android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_preferences)
                method?.invoke(
                    sbm,
                    componentName,
                    "Clipboard Sync",
                    icon,
                    context.mainExecutor,
                    java.util.function.Consumer<Int> { }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    actual val isIgnoringBatteryOptimizations: Boolean
        get() {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }

    actual val canAddQuickSettingsTile: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

@Composable
actual fun rememberSettingsPlatformHelper(): SettingsPlatformHelper {
    val context = LocalContext.current
    return remember(context) { SettingsPlatformHelper(context) }
}
