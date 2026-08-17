package com.dexstudios.dex.core.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

actual fun Modifier.handCursor(): Modifier = this.pointerHoverIcon(PointerIcon.Hand)
