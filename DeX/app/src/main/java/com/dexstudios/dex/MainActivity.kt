package com.dexstudios.dex

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dexstudios.dex.network.DexService
import com.dexstudios.dex.network.KeepAliveWorker
import com.dexstudios.dex.network.MirrorSession
import com.dexstudios.dex.network.SafStorage
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit
import com.dexstudios.dex.ui.theme.DeXTheme
import android.os.Build
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var lastRequestedPermission: String? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val permission = lastRequestedPermission ?: return@registerForActivityResult
        if (!isGranted) {
            if (!androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                when (permission) {
                    Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION ->
                        com.dexstudios.dex.network.PermissionManager.setNearbyPermanentlyDenied(true)
                    Manifest.permission.POST_NOTIFICATIONS ->
                        com.dexstudios.dex.network.PermissionManager.setNotificationsPermanentlyDenied(true)
                }
            }
        } else {
            when (permission) {
                Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION ->
                    com.dexstudios.dex.network.PermissionManager.setNearbyPermanentlyDenied(false)
                Manifest.permission.POST_NOTIFICATIONS ->
                    com.dexstudios.dex.network.PermissionManager.setNotificationsPermanentlyDenied(false)
            }
        }
    }

    private val downloadsDexGrantLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            SafStorage.setDownloadsDexUri(this, uri)
        }
    }

    // PC File Explorer: grant a folder for remote browse + pull over the WebSocket
    private val sharedFolderGrantLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            SafStorage.addSharedFolder(this, uri)
        }
        com.dexstudios.dex.network.SharedFolderGrantState.complete(
            uri,
            if (uri != null) SafStorage.sharedFolderName(this, uri) else ""
        )
    }

    // Screen mirror consent: the PC asked to mirror, this launcher shows the system dialog
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(result.resultCode, result.data!!) ?: run {
                MirrorSession.stop()
                return@registerForActivityResult
            }
            val metrics = resources.displayMetrics
            MirrorSession.start(projection, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        } else {
            MirrorSession.deny() // user denied screen sharing
        }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Trigger permissions from the UI flow
    lifecycleScope.launch {
        com.dexstudios.dex.network.PermissionManager.requestNearby.collect {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.NEARBY_WIFI_DEVICES
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.ACCESS_FINE_LOCATION
            } else null

            permission?.let {
                lastRequestedPermission = it
                requestPermissionLauncher.launch(it)
            }
        }
    }
    lifecycleScope.launch {
        com.dexstudios.dex.network.PermissionManager.requestNotifications.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permission = Manifest.permission.POST_NOTIFICATIONS
                lastRequestedPermission = permission
                requestPermissionLauncher.launch(permission)
            }
        }
    }
    lifecycleScope.launch {
        com.dexstudios.dex.network.PermissionManager.requestFolder.collect {
            downloadsDexGrantLauncher.launch(null)
        }
    }

    // Handle grant requests triggered from the network layer (incoming transfer)
    if (intent?.getBooleanExtra("REQUEST_DOWNLOADS_DEX_GRANT", false) == true) {
        downloadsDexGrantLauncher.launch(null)
    }
    if (intent?.getBooleanExtra("REQUEST_SHARED_FOLDER_GRANT", false) == true) {
        sharedFolderGrantLauncher.launch(null)
    }

    // Start the DeX networking service
    val serviceIntent = Intent(this, DexService::class.java)
    startForegroundService(serviceIntent)

    // Background keep-alive: the periodic worker restarts the service if the OS killed the
    // process, so the phone stays reachable for PC pushes up to 6 hours after last use.
WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        KeepAliveWorker.UNIQUE_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        PeriodicWorkRequestBuilder<KeepAliveWorker>(
            15, TimeUnit.MINUTES
        ).build()
    )

    enableEdgeToEdge()

    // When the PC asks to mirror, surface the system screen-capture consent dialog
    lifecycleScope.launch {
        MirrorSession.pendingConsent.collect { requested ->
            if (requested) {
                val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }
    }

    setContent {
      DeXTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  override fun onResume() {
    super.onResume()
    // Refresh the keep-alive window: the phone stays reachable for 6h after the last app use
    getSharedPreferences(KeepAliveWorker.PREFS, MODE_PRIVATE)
        .edit {
            putLong(com.dexstudios.dex.network.KeepAliveWorker.KEY_LAST_ACTIVE, System.currentTimeMillis())
        }
  }
}
