package com.dexstudios.dex

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.dexstudios.dex.di.appModule
import com.dexstudios.dex.network.DeviceManager
import com.dexstudios.dex.network.TransferHistory
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber
import com.dexstudios.dex.BuildConfig

class DeXApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        DeviceManager.init(this)
        TransferHistory.init(this)
        // Restore persisted "permanently denied" permission flags (survives process restarts)
        com.dexstudios.dex.network.PermissionManager.init(this)

        // Surface persisted Direct Share targets immediately at process start, before
        // discovery completes, so the share sheet offers paired PCs even while offline.
        ShortcutHelper.syncShareShortcuts(this, emptyList())

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        startKoin {
            androidLogger()
            androidContext(this@DeXApplication)
            modules(appModule)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            SingletonImageLoader.get(this).memoryCache?.clear()
        }
    }
}
