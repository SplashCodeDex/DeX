package com.dexstudios.dex

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dexstudios.dex.network.*
import com.dexstudios.dex.ui.share.ShareOverlayWindow
import com.dexstudios.dex.ui.share.ShareTargetSheetContent
import com.dexstudios.dex.ui.share.UploadProgressContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

/**
 * Share-sheet trampoline. Three presentation modes, picked at launch:
 * 1. Direct-send (shortcut tap with a target fingerprint) — no UI, enqueue and exit.
 * 2. Overlay panel ([ShareOverlayWindow]) when `SYSTEM_ALERT_WINDOW` is granted —
 *    the activity turns into an invisible, non-touchable trampoline and the picker
 *    floats over the source app.
 * 3. In-activity translucent bottom sheet otherwise — plus an opt-in row that
 *    deep-links to the overlay permission once.
 *
 * The task is excluded from Recents (manifest) so the picker never lingers in the
 * recent-apps list like a regular activity.
 */
class ShareTargetActivity : ComponentActivity() {

    private companion object {
        // Bounded wait for a Direct Share target to appear in discovery before
        // falling back to the sandbox save. Long enough to cover a cold discovery
        // cycle, short enough to keep the tap-to-result feel responsive.
        const val DIRECT_TARGET_DISCOVERY_TIMEOUT_MS = 6_000L
    }

    private val sharedUris = mutableStateListOf<Uri>()
    private val discoveryEngine: DiscoveryEngine by inject()
    private val clientEngine: ClientEngine by inject()
    private val deviceConfig: DeviceConfig by inject()

    private var overlayWindow: ShareOverlayWindow? = null

    // True only while a visual presentation (overlay panel or bottom sheet) is on
    // screen. The headless direct-send path must never finish on STOP — leaving the
    // app during its bounded discovery wait should let the transfer proceed.
    private var presentationActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ -> }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Handle incoming intent
        val incomingUris = mutableListOf<Uri>()
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { incomingUris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                uris?.let { incomingUris.addAll(it) }
            }
        }
        sharedUris.addAll(incomingUris)

        if (sharedUris.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_no_files), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val targetFingerprint = intent?.getStringExtra(ShortcutHelper.EXTRA_TARGET_FINGERPRINT)
            // Direct Share launches always carry the tapped shortcut's ID — the system
            // injects it into the merged share intent. It is the authoritative routing
            // key when the chooser drops custom extras. (Constant is an inlined string.)
            ?: intent?.getStringExtra("android.intent.extra.SHORTCUT_ID")
        if (targetFingerprint != null) {
            resolveDirectTarget(targetFingerprint)
            return
        }

        if (ShareOverlayWindow.canShowOverlay(this)) {
            presentAsOverlay()
        } else {
            presentAsBottomSheet()
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app (HOME gesture, task switch) exits the share picker exactly
        // like the back gesture: the panel is torn down immediately instead of
        // lingering over the launcher or rotting inside a backgrounded task.
        if (presentationActive) {
            overlayWindow?.dismiss()
            finish()
        }
    }

    override fun onDestroy() {
        // The overlay outlives nothing: if the system tears the trampoline down,
        // the floating panel must go with it.
        overlayWindow?.dismiss()
        overlayWindow = null
        super.onDestroy()
    }

    /**
     * Fallback presentation: translucent activity hosting a ModalBottomSheet.
     * Also the discovery surface for the overlay capability while the permission
     * is missing.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    private fun presentAsBottomSheet() {
        presentationActive = true
        setContent {
            MaterialTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var showSheet by remember { mutableStateOf(true) }
                val discoveredDevices by discoveryEngine.devices.collectAsStateWithLifecycle()
                val uploadState by clientEngine.uploadState.collectAsStateWithLifecycle()
                val (trustedLocal, untrustedDevices) = remember(discoveredDevices) {
                    partitionDevices(discoveredDevices)
                }

                if (showSheet) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            showSheet = false
                            finish()
                        },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    ) {
                        if (uploadState.isUploading || uploadState.isSuccess || uploadState.error != null) {
                            UploadProgressContent(
                                uploadState = uploadState,
                                onCancel = { clientEngine.cancelUpload(this@ShareTargetActivity) },
                                onDone = { finish() },
                                onRetry = { clientEngine.resetUploadState() }
                            )
                        } else {
                            ShareTargetSheetContent(
                                sharedUris = sharedUris,
                                trustedDevices = trustedLocal,
                                untrustedDevices = untrustedDevices,
                                showOverlayOptIn = true,
                                onEnableOverlay = { requestOverlayPermission() },
                                onSaveToSandbox = {
                                    saveToSandbox()
                                    showSheet = false
                                },
                                onSendToDevice = { device ->
                                    sendUrisToDevice(device, sharedUris)
                                    clientEngine.resetUploadState()
                                    startActivity(Intent(this@ShareTargetActivity, MainActivity::class.java))
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Overlay presentation: the activity window becomes fully transparent,
     * non-dimming and non-touchable, so the source app stays visible and keeps
     * receiving touches while the system overlay panel carries the picker.
     */
    private fun presentAsOverlay() {
        presentationActive = true
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        overlayWindow = ShareOverlayWindow(this, onDismissRequest = { finish() }).also { overlay ->
            overlay.show {
                val discoveredDevices by discoveryEngine.devices.collectAsStateWithLifecycle()
                val uploadState by clientEngine.uploadState.collectAsStateWithLifecycle()
                val (trustedLocal, untrustedDevices) = remember(discoveredDevices) {
                    partitionDevices(discoveredDevices)
                }

                if (uploadState.isUploading || uploadState.isSuccess || uploadState.error != null) {
                    UploadProgressContent(
                        uploadState = uploadState,
                        onCancel = { clientEngine.cancelUpload(this@ShareTargetActivity) },
                        onDone = { finish() },
                        onRetry = { clientEngine.resetUploadState() }
                    )
                } else {
                    ShareTargetSheetContent(
                        sharedUris = sharedUris,
                        trustedDevices = trustedLocal,
                        untrustedDevices = untrustedDevices,
                        showOverlayOptIn = false,
                        onEnableOverlay = {},
                        onSaveToSandbox = {
                            saveToSandbox()
                            finish()
                        },
                        onSendToDevice = { device ->
                            sendUrisToDevice(device, sharedUris)
                            clientEngine.resetUploadState()
                        }
                    )
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        // One-time trip to system settings; the next share after granting renders
        // as the floating overlay panel. The sheet stays open for the current share.
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun partitionDevices(
        discovered: Map<String, DiscoveredDevice>
    ): Pair<List<DiscoveredDevice>, List<DiscoveredDevice>> = discovered.values.partition { device ->
        AuthState.pairedFingerprints.contains(device.info.fingerprint) ||
                (device.info.identityHash != null && device.info.identityHash == deviceConfig.identityHash)
    }

    /**
     * Routing for a Direct Share tap on a specific shortcut. Discovery gets a short
     * window to surface the target — the PC is frequently already broadcasting but not
     * yet in the map when the sheet resolves (cold process, wake from sleep). Falls
     * back to the sandbox save when the PC genuinely never appears.
     */
    private fun resolveDirectTarget(fingerprint: String) {
        lifecycleScope.launch {
            val device = withTimeoutOrNull(DIRECT_TARGET_DISCOVERY_TIMEOUT_MS) {
                discoveryEngine.devices
                    .firstOrNull { it.containsKey(fingerprint) }
                    ?.getValue(fingerprint)
            }
            if (device != null) {
                sendUrisToDevice(device, sharedUris)
                finish()
            } else {
                // True AirDrop behavior: keep the share alive behind an ongoing
                // notification and fire it the moment the PC appears, instead of
                // dumping the files into the sandbox. The alias comes from the
                // persisted Direct Share store when known.
                PendingShareForwarder.enqueue(
                    this@ShareTargetActivity,
                    fingerprint,
                    DeviceManager.pairedAliases[fingerprint] ?: "PC",
                    sharedUris.toList()
                )
                finish()
            }
        }
    }

    private fun sendUrisToDevice(device: DiscoveredDevice, uris: List<Uri>) {
        clientEngine.resetUploadState()

        val urisJson = try {
            Json.encodeToString(sharedUris.map { it.toString() })
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        val inputData = workDataOf(
            "ip" to device.ip,
            "port" to device.info.port,
            "uris" to urisJson,
            "targetFingerprint" to device.info.fingerprint,
            "targetAlias" to device.info.alias
        )

        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        clientEngine.activeWorkId = workRequest.id
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun saveToSandbox() {
        lifecycleScope.launch {
            val successCount = withContext(Dispatchers.IO) {
                SafStorage.saveUrisToSandbox(this@ShareTargetActivity, sharedUris.toList())
            }
            if (successCount == sharedUris.size) {
                Toast.makeText(this@ShareTargetActivity, "Saved to DeX Sandbox", Toast.LENGTH_SHORT).show()
            } else if (successCount > 0) {
                Toast.makeText(this@ShareTargetActivity, "Saved $successCount of ${sharedUris.size} files", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ShareTargetActivity, "Failed to save files. Grant folder access in Settings.", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }
}