package com.example.dex.di

import com.example.dex.network.ClientEngine
import com.example.dex.network.DeviceConfig
import com.example.dex.network.DiscoveryEngine
import com.example.dex.network.NotificationHelper
import com.example.dex.network.MessageHandler
import com.example.dex.network.WebSocketClientService
import com.example.dex.ui.main.MainScreenViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { DeviceConfig(androidContext()) }
    single { NotificationHelper(androidContext()) }
    single { ClientEngine() }
    single { DiscoveryEngine(get(), androidContext()) }
    single { MessageHandler(androidContext(), get()) }
    single { WebSocketClientService(get(), get(), get(), androidContext()) }
    
    viewModelOf(::MainScreenViewModel)
}
