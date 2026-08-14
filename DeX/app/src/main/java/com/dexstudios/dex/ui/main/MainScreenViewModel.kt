package com.dexstudios.dex.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexstudios.dex.network.ClientEngine
import com.dexstudios.dex.network.DiscoveryEngine
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.network.WebSocketClientService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainScreenViewModel(
    val discoveryEngine: DiscoveryEngine,
    val clientEngine: ClientEngine,
    private val webSocketClientService: WebSocketClientService
) : ViewModel() {
  val uiState: StateFlow<MainScreenUiState> =
    discoveryEngine.devices
      .map<Map<String, DiscoveredDevice>, MainScreenUiState> { devicesMap -> MainScreenUiState.Success(devicesMap.values.toList()) }
      .catch { emit(MainScreenUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)
      
  fun requestPairing(device: DiscoveredDevice, onResult: (Boolean) -> Unit = {}) {
      // Phone-initiated pairing: ask the PC to push a PIN prompt back to this phone.
      // Trust is established via the PIN exchange; this alone never marks the device as paired.
      // Connects to the tapped PC first (the phone auto-connects to only one target PC, so a
      // different discovered PC needs an explicit switch) and reports the result on the UI thread.
      webSocketClientService.requestPairingWith(device) { ok ->
          viewModelScope.launch { onResult(ok) }
      }
  }

  fun requestUnpair(device: DiscoveredDevice, onResult: (Boolean) -> Unit = {}) {
      // Asks the PC to forget this device from its paired_devices.json.
      webSocketClientService.requestUnpairWith(device) { ok ->
          viewModelScope.launch { onResult(ok) }
      }
  }
}

sealed interface MainScreenUiState {
  object Loading : MainScreenUiState
  data class Error(val throwable: Throwable) : MainScreenUiState
  data class Success(val data: List<DiscoveredDevice>) : MainScreenUiState
}
