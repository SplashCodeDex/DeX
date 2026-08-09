package com.dexstudios.dex.di

import com.dexstudios.dex.network.ClientEngine
import com.dexstudios.dex.network.ClipboardSyncManager
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.network.DiscoveryEngine
import com.dexstudios.dex.network.FileShareManager
import com.dexstudios.dex.network.NotificationHelper
import com.dexstudios.dex.network.MessageHandler
import com.dexstudios.dex.network.QuicClient
import com.dexstudios.dex.network.PunchSession
import com.dexstudios.dex.network.WebSocketClientService
import com.dexstudios.dex.ui.main.MainScreenViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { DeviceConfig(androidContext()) }
    single { NotificationHelper(androidContext()) }
    single { QuicClient(androidContext()) }
    single { ClientEngine(quicClient = get(), deviceConfig = get()) }
    single { DiscoveryEngine(get(), androidContext()) }
    single { FileShareManager(get(), get(), androidContext()) }
    single { MessageHandler(get(), androidContext(), get(), get()) }
    single { WebSocketClientService(get(), get(), get(), androidContext()) }
    single { ClipboardSyncManager(androidContext(), get(), get(), get()) }
    single { PunchSession(get(), get(), get(), androidContext()) }
    
    viewModelOf(::MainScreenViewModel)
}
