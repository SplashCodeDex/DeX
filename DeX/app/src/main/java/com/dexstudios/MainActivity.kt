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
import com.dexstudios.dex.network.SafStorage
import com.dexstudios.dex.ui.theme.DeXTheme
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val downloadsDexGrantLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            SafStorage.setDownloadsDexUri(this, uri)
        }
    }

    private val folderGrantLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = pendingFolderGrantName ?: "Folder"
            SafStorage.addGrantedFolder(this, name, uri)
            pendingFolderGrantName = null
        }
    }

    private var pendingFolderGrantName: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    
    // Handle grant requests triggered from the network layer (incoming transfer / file explorer)
    if (intent?.getBooleanExtra("REQUEST_DOWNLOADS_DEX_GRANT", false) == true) {
        downloadsDexGrantLauncher.launch(null)
    } else if (intent?.hasExtra("REQUEST_FOLDER_GRANT") == true) {
        pendingFolderGrantName = intent.getStringExtra("REQUEST_FOLDER_GRANT")
        folderGrantLauncher.launch(null)
    }
    
    // Start the DeX networking service
    val serviceIntent = android.content.Intent(this, com.dexstudios.dex.network.DexService::class.java)
    startForegroundService(serviceIntent)

    // Background keep-alive: the periodic worker restarts the service if the OS killed the
    // process, so the phone stays reachable for PC pushes up to 6 hours after last use.
    androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        com.dexstudios.dex.network.KeepAliveWorker.UNIQUE_NAME,
        androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
        androidx.work.PeriodicWorkRequestBuilder<com.dexstudios.dex.network.KeepAliveWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        ).build()
    )

    enableEdgeToEdge()
    setContent {
      DeXTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  override fun onResume() {
    super.onResume()
    // Refresh the keep-alive window: the phone stays reachable for 6h after the last app use
    getSharedPreferences(com.dexstudios.dex.network.KeepAliveWorker.PREFS, MODE_PRIVATE)
        .edit()
        .putLong(com.dexstudios.dex.network.KeepAliveWorker.KEY_LAST_ACTIVE, System.currentTimeMillis())
        .apply()
  }
}