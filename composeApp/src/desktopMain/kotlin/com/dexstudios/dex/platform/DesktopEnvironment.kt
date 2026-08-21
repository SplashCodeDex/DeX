package com.dexstudios.dex.platform

object DesktopEnvironment {
    private val osName = System.getProperty("os.name")?.lowercase() ?: ""

    val isWindows: Boolean = osName.contains("windows")
    val isMacOS: Boolean = osName.contains("mac")
    val isLinux: Boolean = osName.contains("linux")
}
