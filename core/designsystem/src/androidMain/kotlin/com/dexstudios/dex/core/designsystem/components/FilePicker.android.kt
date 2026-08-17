package com.dexstudios.dex.core.designsystem.components

import androidx.compose.runtime.Composable

@Composable
actual fun FilePickerDialog(
    show: Boolean,
    onFilesSelected: (List<String>?) -> Unit
) {
    // Left empty for now, as Android uses system pickers via Intents outside of pure composable bounds
}
