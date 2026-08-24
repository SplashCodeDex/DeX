package com.dexstudios.dex.core.network

import java.net.InetAddress

actual fun getPlatformDeviceName(): String = runCatching { InetAddress.getLocalHost().hostName }.getOrNull() ?: "Desktop PC"
actual fun getPlatformDeviceModel(): String = System.getProperty("os.name") ?: "Desktop PC"
actual fun getPlatformDeviceType(): String = "desktop"
