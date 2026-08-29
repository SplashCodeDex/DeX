package com.dexstudios.dex

import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dexstudios.dex.network.DeviceManager
import com.dexstudios.dex.network.DiscoveredDevice
import timber.log.Timber

/**
 * Direct Share bridge: publishes long-lived sharing shortcuts so paired PCs appear
 * as one-tap targets in the system share sheet's direct row (the AirDrop-style tap).
 *
 * The shortcut ID is the peer's fingerprint. The system hands it back to
 * [ShareTargetActivity] via `EXTRA_SHORTCUT_ID` whenever the target is tapped, so
 * routing never depends on extra-merging behavior of the chooser. Targets persist
 * across process death and offline periods because they are long-lived and backed
 * by [DeviceManager]'s persisted alias store.
 */
object ShortcutHelper {
    private const val CATEGORY_SHARE_TARGET = "com.dexstudios.dex.category.DIRECT_SHARE_TARGET"
    const val EXTRA_TARGET_FINGERPRINT = "EXTRA_TARGET_FINGERPRINT"

    // Launchers surface at most 4 direct-share targets per app; keep parity so ranking stays honest.
    private const val MAX_SHARE_TARGETS = 4

    /**
     * Reconciles the share-sheet target list with current reality:
     * online trusted devices lead (ranked by discovery order), followed by
     * offline paired devices restored from the persisted alias store.
     *
     * Called from the discovery sync loop and once at process start.
     */
    fun syncShareShortcuts(context: Context, onlineTrusted: List<DiscoveredDevice>) {
        // Persist the freshest alias per online target so offline renders and labels
        // stay correct after reboot, sleep, or process death.
        onlineTrusted.forEach { DeviceManager.savePairedAlias(it.info.fingerprint, it.info.alias) }

        val knownAliases = DeviceManager.pairedAliases
        if (knownAliases.isEmpty()) return

        val onlineIds = onlineTrusted.map { it.info.fingerprint }.toSet()
        val ordered = onlineTrusted.map { it.info.fingerprint to it.info.alias } +
                knownAliases.filterKeys { it !in onlineIds }.map { (fp, alias) -> fp to alias }

        val shortcuts = ordered.take(MAX_SHARE_TARGETS).mapIndexed { rank, (fingerprint, alias) ->
            ShortcutInfoCompat.Builder(context, fingerprint)
                .setShortLabel(alias)
                .setLongLabel(context.getString(R.string.share_direct_send_to, alias))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(context, ShareTargetActivity::class.java).apply {
                        action = Intent.ACTION_SEND
                        putExtra(EXTRA_TARGET_FINGERPRINT, fingerprint)
                    }
                )
                .setCategories(setOf(CATEGORY_SHARE_TARGET))
                .setPerson(
                    Person.Builder()
                        .setName(alias)
                        .setKey(fingerprint)
                        .build()
                )
                // Long-lived keeps the target in the sheet even while the process is
                // dead or the PC is offline — the behavior that makes the target feel
                // system-provided rather than app-provided.
                .setLongLived(true)
                .setRank(rank)
                .build()
        }

        try {
            // Replace (not add) so unpaired/stale targets fall out of the dynamic list
            // in the same pass that publishes the fresh ones.
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: Exception) {
            Timber.e(e, "Failed to publish Direct Share shortcuts")
        }
    }

    /**
     * Purges a target from the share sheet. The long-lived copy survives a dynamic
     * removal, so it must be cleared explicitly — otherwise forgotten PCs would
     * linger in the sheet forever.
     */
    fun removeShortcut(context: Context, fingerprint: String) {
        try {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(fingerprint))
            ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(fingerprint))
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove Direct Share shortcut for $fingerprint")
        }
    }
}
