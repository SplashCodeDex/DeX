package com.example.dex.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dex.network.ClientEngine
import com.example.dex.network.DiscoveryEngine
import com.example.dex.network.DiscoveredDevice
import com.example.dex.network.WebSocketClientService
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
      viewModelScope.launch {
          val sent = webSocketClientService.sendPairRequest(device.info.fingerprint)
          onResult(sent)
      }
  }
  
  fun sendClipboard(device: DiscoveredDevice, text: String, onResult: (Boolean) -> Unit) {
      viewModelScope.launch {
          val success = clientEngine.sendClipboard(device.ip, device.info.port, text, device.info.fingerprint, device.info.identityHash)
          onResult(success)
      }
  }
}

sealed interface MainScreenUiState {
  object Loading : MainScreenUiState
  data class Error(val throwable: Throwable) : MainScreenUiState
  data class Success(val data: List<DiscoveredDevice>) : MainScreenUiState
}
