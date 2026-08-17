package com.dexstudios.dex.core.network

import android.os.Build

actual fun getPlatformDeviceName(): String = Build.MODEL ?: "Android Device"
actual fun getPlatformDeviceModel(): String = Build.MODEL ?: "Android"
actual fun getPlatformDeviceType(): String = "mobile"
