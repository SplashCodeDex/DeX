package com.dexstudios.dex.network

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import org.koin.android.ext.android.inject

@RequiresApi(Build.VERSION_CODES.N)
class ClipboardSyncTileService : TileService() {
    private val deviceConfig: DeviceConfig by inject()

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val newState = !deviceConfig.clipboardSyncEnabled
        deviceConfig.clipboardSyncEnabled = newState
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val enabled = deviceConfig.clipboardSyncEnabled
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
