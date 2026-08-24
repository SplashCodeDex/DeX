package com.dexstudios.dex.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.WebSocketEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainScreenViewModel(val discoveryEngine: DiscoveryEngine, val clientEngine: ClientEngine, val webSocketEngine: WebSocketEngine) : ViewModel() {
    val uiState: StateFlow<MainScreenUiState> =
        discoveryEngine.devices
            .map<Map<String, DiscoveredDevice>, MainScreenUiState> { devicesMap -> MainScreenUiState.Success(devicesMap.values.toList()) }
            .catch { emit(MainScreenUiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

    fun requestPairing(device: DiscoveredDevice, onResult: (Boolean) -> Unit = {}) {
        webSocketEngine.requestPairingWith(device, onResult)
    }

    fun requestUnpair(device: DiscoveredDevice, onResult: (Boolean) -> Unit = {}) {
        webSocketEngine.requestUnpairWith(device, onResult)
    }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val data: List<DiscoveredDevice>) : MainScreenUiState
}
