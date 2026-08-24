package com.dexstudios.dex

import androidx.compose.runtime.Composable
import com.dexstudios.dex.core.designsystem.theme.DeXTheme

/**
 * Legacy commonMain App entry point.
 *
 * As per the DeX Desktop Migration Plan:
 * The Desktop CMP application now natively configures its own translucent Window
 * and initializes via `FloatingDockCard` directly in `desktopMain/main.kt`.
 *
 * Android is maintained as a separate standalone project at `W:\CodeDeX\DeX\DeX`.
 */
@Composable
fun App() {
    DeXTheme {
        // Desktop natively injects FloatingDockCard from main.kt
    }
}
