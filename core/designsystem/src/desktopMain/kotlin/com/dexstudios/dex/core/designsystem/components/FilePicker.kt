package com.dexstudios.dex.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.awt.FileDialog
import java.awt.Frame

// DESKTOP-ONLY by nature: this is a blocking AWT file dialog, an API that has no analogue on
// Android (where the platform hands you a Storage Access Framework Uri, not a filesystem path).
//
// It used to sit behind `expect fun FilePickerDialog` in commonMain with this file as the lone
// `actual`. An expect/actual with only one implementation is an empty promise — it forced every
// other target to declare a picker it could not honour (it is what first broke the Android
// target of this module), and no caller ever used the indirection. The behaviour is unchanged
// and kept in full here.
//
// When file-picking is genuinely centralised (advisor note: picker-service centralization), the
// seam should be designed across BOTH platforms at once: an Android actual resolving SAF Uris,
// the desktop one resolving AWT paths, and a shared result type that can carry either.
@Composable
fun FilePickerDialog(show: Boolean, onFilesSelected: (List<String>?) -> Unit) {
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
