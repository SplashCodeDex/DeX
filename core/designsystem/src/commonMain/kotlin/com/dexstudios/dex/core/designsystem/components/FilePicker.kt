package com.dexstudios.dex.core.designsystem.components

import androidx.compose.runtime.Composable

@Composable
expect fun FilePickerDialog(
    show: Boolean,
    onFilesSelected: (List<String>?) -> Unit
)
