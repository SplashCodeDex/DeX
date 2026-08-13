package com.dexstudios.dex.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.R

@Preview(showBackground = true)
@Composable
fun DevicesOutlinedPreview() {
    Image(
        imageVector = ImageVector.vectorResource(id = R.drawable.ic_devices_outlined),
        contentDescription = null,
        modifier = Modifier.size(300.dp)
    )
}
