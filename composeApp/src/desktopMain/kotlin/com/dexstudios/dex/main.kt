package com.dexstudios.dex

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.core.network.di.commonNetworkModule
import com.dexstudios.dex.core.network.di.desktopNetworkModule
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.server.DeXServer
import com.dexstudios.dex.window.DockedWindowStateController
import com.dexstudios.dex.window.FloatingDockCard
import org.koin.core.context.startKoin
import org.koin.dsl.module
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import okio.Path.Companion.toPath
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.dex_logo
import dev.nucleusframework.composenativetray.tray.api.Tray

val desktopAppModule = module {
    single<kotlinx.coroutines.CoroutineScope> { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()) }

    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { File(System.getProperty("user.home"), ".dex_settings.preferences_pb").absolutePath.toPath() }
        )
    }

    single { DeviceConfig(get(), get()) }
}

fun main() {
    if (org.koin.core.context.GlobalContext.getOrNull() == null) {
        startKoin {
            modules(desktopAppModule, commonNetworkModule, desktopNetworkModule)
        }
    }

    val discoveryEngine = org.koin.java.KoinJavaComponent.getKoin().get<com.dexstudios.dex.core.network.DiscoveryEngine>()
    discoveryEngine.startDiscovery()

    try {
        DeXServer.start()
    } catch (e: Exception) {
        println("DeXServer already running or failed to start: ")
    }

    application {
        val coroutineScope = rememberCoroutineScope()
        val controller = remember(coroutineScope) {
            DockedWindowStateController(scope = coroutineScope).apply {
                show()
            }
        }

        LaunchedEffect(Unit) {
            val deviceConfig = org.koin.java.KoinJavaComponent.getKoin().get<DeviceConfig>()
            com.dexstudios.dex.desktop.jna.WiggleToOpenService.start(
                deviceConfig = deviceConfig,
                onWake = { controller.hide() },
                onTrigger = { controller.show() }
            )
            com.dexstudios.dex.desktop.jna.GlobalShortcutService.start {
                controller.toggleVisibility()
            }
            com.dexstudios.dex.desktop.jna.ClipboardSyncService.start(deviceConfig)
        }

        // 300ms Click Debounce Filter for Tray Action
        var lastTrayClickTime by remember { mutableStateOf(0L) }
        val toggleWithDebounce: () -> Unit = {
            val now = System.currentTimeMillis()
            if (now - lastTrayClickTime >= 300L) {
                lastTrayClickTime = now
                // Prevent race condition: if clicking the tray icon just caused the window to lose focus
                // and hide itself, don't immediately toggle it back to visible.
                if (now - controller.lastHideTime > 250L) {
                    controller.toggleVisibility()
                }
            }
        }

        // Native System Tray with 300ms debounce and Context Menu
        Tray(
            icon = Res.drawable.dex_logo,
            tooltip = "DeX",
            primaryAction = toggleWithDebounce,
            menuContent = {
                Item(
                    label = if (controller.isVisible) "Hide DeX" else "Show DeX",
                    onClick = toggleWithDebounce
                )
                Divider()
                Item(
                    label = "Quit",
                    onClick = {
                        DesktopShutdownCoordinator.stopAllServices()
                        exitApplication()
                        kotlin.system.exitProcess(0)
                    }
                )
            }
        )

        // Gates actual AWT visibility until the UTILITY window type is applied
        // (AWT setType() must run before the native peer is created).
        var windowReady by remember { mutableStateOf(false) }

        // Anti-Flash Cache: Delay hiding the OS window by 150ms to allow the Compose exit animation to finish.
        // This ensures the OS caches a 100% transparent bitmap when hidden, preventing the "double opening" flash.
        var isWindowReallyVisible by remember { mutableStateOf(false) }

        LaunchedEffect(controller.isVisible, windowReady) {
            if (controller.isVisible && windowReady) {
                isWindowReallyVisible = true
            } else {
                if (windowReady) kotlinx.coroutines.delay(150)
                isWindowReallyVisible = false
            }
        }

        Window(
            onCloseRequest = { controller.hide() },
            visible = isWindowReallyVisible,
            state = controller.windowState,
            undecorated = true,
            transparent = true,
            alwaysOnTop = true,
            resizable = false,
            title = "DeX"
        ) {
            DeXTheme {
                LaunchedEffect(window) {
                    // Taskbar icon suppression via AWT UTILITY window type (if not already displayable)
                    try {
                        if (!window.isDisplayable) {
                            window.type = java.awt.Window.Type.UTILITY
                        }
                    } catch (_: Throwable) {
                        // Ignored if window peer is already created
                    }

                    // Native AWT DropTarget for external Windows Explorer file transfers
                    window.dropTarget = DropTarget().apply {
                        addDropTargetListener(object : DropTargetAdapter() {
                            override fun drop(dtde: DropTargetDropEvent) {
                                try {
                                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                                    val droppedFiles = dtde.transferable.getTransferData(
                                        java.awt.datatransfer.DataFlavor.javaFileListFlavor
                                    ) as? List<*>
                                    val files = droppedFiles?.filterIsInstance<File>() ?: emptyList()
                                    if (files.isNotEmpty()) {
                                        println("Dropped ${files.size} external files onto DeX window")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        })
                    }

                    // Only now show the window — the UTILITY type is applied while the
                    // peer is still non-displayable, which removes the taskbar icon.
                    windowReady = true
                }

                // 5-Point Safety Guard Focus Loss Listener
                DisposableEffect(window) {
                    val listener = object : java.awt.event.WindowFocusListener {
                        override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {}
                        override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                            if (controller.shouldDismissOnFocusLoss()) {
                                controller.hide()
                            }
                        }
                    }
                    window.addWindowFocusListener(listener)
                    onDispose { window.removeWindowFocusListener(listener) }
                }

                FloatingDockCard(
                    window = window,
                    controller = controller,
                    onDismiss = { controller.hide() },
                    onExitEngine = {
                        DesktopShutdownCoordinator.stopAllServices()
                        exitApplication()
                        kotlin.system.exitProcess(0)
                    }
                )
            }
        }
    }
}

