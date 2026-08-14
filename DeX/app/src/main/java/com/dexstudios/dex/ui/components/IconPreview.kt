package com.dexstudios.dex.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.ui.icons.MaterialSymbols

@Preview(showBackground = true)
@Composable
fun DevicesOutlinedPreview() {
    Image(
        imageVector = MaterialSymbols.Devices,
        contentDescription = null,
        modifier = Modifier.size(300.dp)
    )
}
