package com.dexstudios.dex

import android.content.Intent
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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results: Map<String, Boolean> ->
        results.forEach { (permission, isGranted) ->
            if (!isGranted) {
                if (!androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    when (permission) {
                        Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION ->
                            com.dexstudios.dex.network.PermissionManager.setNearbyPermanentlyDenied(true)
                        Manifest.permission.POST_NOTIFICATIONS ->
                            com.dexstudios.dex.network.PermissionManager.setNotificationsPermanentlyDenied(true)
                        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                        Manifest.permission.READ_EXTERNAL_STORAGE ->
                            com.dexstudios.dex.network.PermissionManager.setMediaPermanentlyDenied(true)
                    }
                }
            } else {
                when (permission) {
                    Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION ->
                        com.dexstudios.dex.network.PermissionManager.setNearbyPermanentlyDenied(false)
                    Manifest.permission.POST_NOTIFICATIONS ->
                        com.dexstudios.dex.network.PermissionManager.setNotificationsPermanentlyDenied(false)
                    Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                    Manifest.permission.READ_EXTERNAL_STORAGE ->
                        com.dexstudios.dex.network.PermissionManager.setMediaPermanentlyDenied(false)
                }
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

  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Trigger permissions from the UI flow
    // Essentials: one combined system dialog for whichever of nearby/notifications is missing
    lifecycleScope.launch {
        com.dexstudios.dex.network.PermissionManager.requestEssentials.collect {
            val missing = mutableListOf<String>()
            val nearbyPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.NEARBY_WIFI_DEVICES
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.ACCESS_FINE_LOCATION
            } else null

            if (nearbyPermission != null && ContextCompat.checkSelfPermission(this@MainActivity, nearbyPermission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missing.add(nearbyPermission)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (missing.isNotEmpty()) {
                requestMultiplePermissionsLauncher.launch(missing.toTypedArray())
            }
        }
    }
    lifecycleScope.launch {
        com.dexstudios.dex.network.PermissionManager.requestMedia.collect {
            if (!com.dexstudios.dex.network.checkHasMediaPermission(this@MainActivity)) {
                requestMultiplePermissionsLauncher.launch(com.dexstudios.dex.network.getMediaPermissions())
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
    runCatching { startForegroundService(serviceIntent) }

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
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

    setContent {
      val windowSizeClass = androidx.compose.material3.windowsizeclass.calculateWindowSizeClass(this)
      DeXTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
          MainNavigation(
            windowSizeClass = windowSizeClass,
            onDismiss = { moveTaskToBack(true) }
          )
        }
      }
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
