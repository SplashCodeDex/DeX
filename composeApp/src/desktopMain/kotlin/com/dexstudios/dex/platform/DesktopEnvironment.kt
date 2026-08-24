package com.dexstudios.dex.platform

object DesktopEnvironment {
    private val osName = System.getProperty("os.name")?.lowercase() ?: ""

    val isWindows: Boolean = osName.contains("windows")
    val isMacOS: Boolean = osName.contains("mac")
    val isLinux: Boolean = osName.contains("linux")

    /**
     * Human-readable label of the global show/hide shortcut actually registered by this
     * build ([com.dexstudios.dex.desktop.jna.GlobalShortcutService] installs Win+Shift+D on
     * Windows only). Empty on platforms with no registered shortcut, so UI must hide any
     * shortcut hint instead of advertising a fake one.
     */
    val globalToggleShortcutHint: String = when {
        isWindows -> "Win+Shift+D"
        else -> ""
    }
}
