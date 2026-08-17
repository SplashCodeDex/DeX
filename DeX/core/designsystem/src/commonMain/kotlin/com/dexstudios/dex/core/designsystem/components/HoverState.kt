package com.dexstudios.dex.core.designsystem.components

import androidx.compose.ui.Modifier

/**
 * Changes the mouse cursor to a hand pointer when hovering over the component on Desktop/Web.
 * No-op on Android/iOS touch devices.
 */
expect fun Modifier.handCursor(): Modifier
