package com.dexstudios.dex

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dexstudios.dex.network.DiscoveredDevice

object ShortcutHelper {
    private const val CATEGORY_SHARE_TARGET = "com.dexstudios.dex.category.DIRECT_SHARE_TARGET"

    fun updateShareShortcuts(context: Context, devices: List<DiscoveredDevice>) {
        // Only trusted/paired devices should be share targets
        // For simplicity here, we assume the caller filters for trusted devices.

        val shortcuts = devices.take(4).map { device ->
            val intent = Intent(context, ShareTargetActivity::class.java).apply {
                action = Intent.ACTION_SEND
                putExtra("EXTRA_TARGET_FINGERPRINT", device.info.fingerprint)
                // Add categories if needed, but normally just action and target
            }

            val iconRes = R.mipmap.ic_launcher

            ShortcutInfoCompat.Builder(context, device.info.fingerprint)
                .setShortLabel(device.info.alias)
                .setLongLabel("Send to ${device.info.alias}")
                .setIcon(IconCompat.createWithResource(context, iconRes))
                .setIntent(intent)
                .setCategories(setOf(CATEGORY_SHARE_TARGET))
                .setPerson(
                    androidx.core.app.Person.Builder()
                        .setName(device.info.alias)
                        .setKey(device.info.fingerprint)
                        .build()
                )
                .build()
        }

        ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
    }

    fun removeShortcut(context: Context, fingerprint: String) {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(fingerprint))
    }
}
