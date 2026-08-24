package com.dexstudios.dex.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun FilePickerDialog(show: Boolean, onFilesSelected: (List<String>?) -> Unit) {
    if (show) {
        LaunchedEffect(Unit) {
            val dialog = FileDialog(null as Frame?, "Select File to Send", FileDialog.LOAD)
            dialog.isMultipleMode = true
            dialog.isVisible = true
            val files = dialog.files
            if (files != null && files.isNotEmpty()) {
                onFilesSelected(files.map { it.absolutePath })
            } else {
                onFilesSelected(null)
            }
        }
    }
}
