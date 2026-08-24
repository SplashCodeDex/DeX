package com.dexstudios.dex.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dexstudios.dex.auth.PairingEngine
import com.dexstudios.dex.window.components.PinPairingPanel as InternalPinPairingPanel

@Composable
fun PinPairingPanel(pairingEngine: PairingEngine, onClose: () -> Unit, modifier: Modifier = Modifier) {
    InternalPinPairingPanel(
        pairingEngine = pairingEngine,
        onClose = onClose,
        modifier = modifier,
    )
}
