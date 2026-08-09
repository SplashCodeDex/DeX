package com.dexstudios.dex

import android.app.Application
import com.dexstudios.dex.di.appModule
import com.dexstudios.dex.network.DeviceManager
import com.dexstudios.dex.network.TransferHistory
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber
import com.dexstudios.dex.BuildConfig

class DeXApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        DeviceManager.init(this)
        TransferHistory.init(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        startKoin {
            androidLogger()
            androidContext(this@DeXApplication)
            modules(appModule)
        }
    }
}
