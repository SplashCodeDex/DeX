package com.dexstudios.dex.core.network



interface IDiscoveryService {
    fun start(localInfo: RegisterDto, onDeviceDiscovered: (DiscoveredDevice) -> Unit)
    fun stop()
}
