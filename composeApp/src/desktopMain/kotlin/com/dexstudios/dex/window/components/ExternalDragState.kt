package com.dexstudios.dex.window.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Observable holder tracking whether an external OS file drag is currently hovering over the DeX window.
 */
class ExternalDragState {
    var isExternalDragActive by mutableStateOf(false)
    var isDeviceSectionHovered by mutableStateOf(false)
}

val LocalExternalDragState = compositionLocalOf { ExternalDragState() }
