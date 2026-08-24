package com.dexstudios.dex

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.dex_logo
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.di.commonNetworkModule
import com.dexstudios.dex.core.network.di.desktopNetworkModule
import com.dexstudios.dex.core.network.server.DeXServer
import com.dexstudios.dex.window.DockedWindowStateController
import com.dexstudios.dex.window.FloatingDockCard
import dev.nucleusframework.composenativetray.tray.api.Tray
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.awt.Taskbar
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Single-flight guard: a second Quit click must never spawn a rival teardown thread whose
 *  exitProcess(0) could kill the first thread's half-finished shutdown. */
private val quitStarted = AtomicBoolean(false)

/**
 * User-initiated quit path. The blocking teardown runs on a side thread so the EDT never
 * freezes for up to ~7s with live connections; the process then hard-exits from that
 * thread. The JVM shutdown hook remains the synchronous safety net for crash paths.
 */
fun quitDesktopApp() {
    if (!quitStarted.compareAndSet(false, true)) return
    Thread({
        DesktopShutdownCoordinator.stopAllServices()
        kotlin.system.exitProcess(0)
    }, "dex-quit").start()
}

val desktopAppModule = module {
    single<kotlinx.coroutines.CoroutineScope> { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()) }

    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { File(System.getProperty("user.home"), ".dex_settings.preferences_pb").absolutePath.toPath() },
        )
    }

    single { DeviceConfig(get(), get()) }
    single { com.dexstudios.dex.desktop.transfer.DesktopFileSendService(get(), get(), get()) }
}

fun main() {
    if (org.koin.core.context.GlobalContext.getOrNull() == null) {
        startKoin {
            modules(desktopAppModule, commonNetworkModule, desktopNetworkModule)
        }
    }

    // Safety net for every exit path that bypasses the explicit Quit handlers: uncaught
    // exceptions, OS logoff/shutdown and taskkill. Idempotent — see DesktopShutdownCoordinator.
    Runtime.getRuntime().addShutdownHook(
        Thread({ DesktopShutdownCoordinator.stopAllServices() }, "dex-shutdown"),
    )

    val discoveryEngine = org.koin.java.KoinJavaComponent.getKoin().get<com.dexstudios.dex.core.network.DiscoveryEngine>()
    discoveryEngine.startDiscovery()

    // Hydrate persisted pairing trust (fingerprints + tokens) BEFORE the server accepts any
    // connection; without this AuthState starts empty and savePaired* hits an uninitialized
    // DataStore, silently breaking PIN-pair acceptance and cross-restart trust. A corrupt
    // store must degrade to empty trust, never block the app from starting.
    runBlocking {
        runCatching {
            com.dexstudios.dex.core.network.DeviceManager.init(
                org.koin.java.KoinJavaComponent.getKoin().get<DataStore<Preferences>>(),
            )
        }.onFailure { e ->
            java.util.logging.Logger.getLogger("DeX").warning(
                "Pairing trust store failed to load; starting with empty trust. ${e::class.simpleName}: ${e.message}",
            )
        }
    }

    try {
        DeXServer.start()
    } catch (e: Exception) {
        e.printStackTrace()
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "Failed to start DeX internal server.\nEnsure ports 48424 (HTTPS), 48425 (sign-in) and 48426 (pull fallback) are not already in use by another instance.\nError: ${e.message}",
            "DeX Startup Error",
            javax.swing.JOptionPane.ERROR_MESSAGE,
        )
        kotlin.system.exitProcess(1)
    }

    // WAN reachability: open UPnP mappings and resolve our public address so same-account
    // phones can reach this PC from cellular networks without manual port forwarding.
    runCatching {
        org.koin.java.KoinJavaComponent.getKoin()
            .get<com.dexstudios.dex.core.network.services.DesktopUpnpService>()
            .configureAsync()
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
                onTrigger = { controller.show() },
            )
            com.dexstudios.dex.desktop.jna.GlobalShortcutService.start {
                controller.toggleVisibility()
            }
            com.dexstudios.dex.desktop.jna.ClipboardSyncService.start(deviceConfig)
            com.dexstudios.dex.desktop.AutoAdbHotspotService.start(
                deviceConfig = deviceConfig,
                devicesFlow = org.koin.java.KoinJavaComponent.getKoin()
                    .get<com.dexstudios.dex.core.network.DiscoveryEngine>().devices,
            )
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
                    onClick = toggleWithDebounce,
                )
                Divider()
                Item(
                    label = "Quit",
                    onClick = {
                        // Immediate visual feedback while services wind down off-EDT
                        controller.hide()
                        quitDesktopApp()
                    },
                )
            },
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
            title = "DeX",
        ) {
            val clientEngine = remember { org.koin.java.KoinJavaComponent.getKoin().get<com.dexstudios.dex.core.network.ClientEngine>() }
            val fileSender = remember { org.koin.java.KoinJavaComponent.getKoin().get<com.dexstudios.dex.desktop.transfer.DesktopFileSendService>() }
            val uploadState by clientEngine.uploadState.collectAsState()

            // Coalesce to integer percent: the raw float progress ticks far more often than
            // the native taskbar API cares about, and each tick re-fired this effect.
            val taskbarProgressPercent = (uploadState.progress * 100).toInt()

            LaunchedEffect(window, uploadState.isUploading, taskbarProgressPercent, uploadState.error != null) {
                try {
                    if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)) {
                        val tb = Taskbar.getTaskbar()
                        if (uploadState.isUploading) {
                            tb.setWindowProgressState(window, Taskbar.State.NORMAL)
                            tb.setWindowProgressValue(window, taskbarProgressPercent)
                        } else if (uploadState.error != null) {
                            tb.setWindowProgressState(window, Taskbar.State.ERROR)
                            tb.setWindowProgressValue(window, 100)
                        } else {
                            tb.setWindowProgressState(window, Taskbar.State.OFF)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore taskbar errors
                }
            }

            // Theme override from Settings > Appearance: System / Dark / Light. The
            // persisted choice wins over the OS setting; System defers to isSystemInDarkTheme.
            val deviceConfigForTheme = remember { org.koin.java.KoinJavaComponent.getKoin().get<DeviceConfig>() }
            val themeOverride by deviceConfigForTheme.themeOverrideFlow.collectAsState()

            DeXTheme(
                darkTheme = when (themeOverride) {
                    DeviceConfig.THEME_DARK -> true
                    DeviceConfig.THEME_LIGHT -> false
                    else -> isSystemInDarkTheme()
                },
            ) {
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
                                        java.awt.datatransfer.DataFlavor.javaFileListFlavor,
                                    ) as? List<*>
                                    val files = droppedFiles?.filterIsInstance<File>()?.filter { it.isFile } ?: emptyList()
                                    if (files.isNotEmpty()) {
                                        fileSender.sendFiles(files)
                                    }
                                    dtde.dropComplete(files.isNotEmpty())
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    runCatching { dtde.dropComplete(false) }
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
                        // Immediate visual feedback while services wind down off-EDT
                        controller.hide()
                        quitDesktopApp()
                    },
                )
            }
        }
    }
}
