package com.dexstudios.dex.core.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

// ============================================================================
// DeX Button Wrappers
// These replace standard Material3 buttons to ensure spatial physics are applied globally.
// ============================================================================

@Composable
fun DeXButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.shape,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.bubbleFluidity().handCursor(),
        enabled = enabled,
        colors = colors,
        shape = shape,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun DeXTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.bubbleFluidity().handCursor(),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}
