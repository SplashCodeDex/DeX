package com.dexstudios.dex.window.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.icons.KeyboardShiftGlyph
import com.dexstudios.dex.core.designsystem.icons.MouseGlyph
import com.dexstudios.dex.platform.ShiftKeyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Live Shift+Click affordance pinned to the far right of the Exit Engine button. Polls
 * the global Shift modifier while [enabled] and reflects it in real time: the Shift arrow
 * fills and the mouse's primary button blinks/ripples the moment Shift is held,
 * previewing the exact gesture right where it applies.
 *
 * The dock menu keeps this row composed even while hidden (visibility is painted via
 * graphicsLayer alpha, not composition removal), so [enabled] must track the card's
 * visibility - passing false cancels the poller entirely instead of burning a 64ms
 * GetAsyncKeyState wake-up forever behind an invisible surface.
 */
@Composable
internal fun rememberShiftHeld(pollIntervalMs: Long = 64L, enabled: Boolean = true): Boolean {
    var shiftHeld by remember { mutableStateOf(false) }
    LaunchedEffect(pollIntervalMs, enabled) {
        if (!enabled) {
            shiftHeld = false // Never reopen with a stale filled glyph from the last session.
            return@LaunchedEffect
        }
        while (true) {
            shiftHeld = withContext(Dispatchers.IO) { ShiftKeyState.isShiftHeldNow() }
            delay(pollIntervalMs)
        }
    }
    return shiftHeld
}

@Composable
internal fun ShiftClickCombo(modifier: Modifier = Modifier, isPanelVisible: Boolean = true) {
    val shiftHeld = rememberShiftHeld(enabled = isPanelVisible)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        KeyboardShiftGlyph(isFilled = shiftHeld, size = 15.dp)
        Text(
            text = "+",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MouseGlyph(buttonActive = shiftHeld, size = 15.dp)
    }
}
