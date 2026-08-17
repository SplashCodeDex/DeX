package com.dexstudios.dex.network



interface IDiscoveryService {
    fun start(localInfo: RegisterDto, onDeviceDiscovered: (DiscoveredDevice) -> Unit)
    fun stop()
}
