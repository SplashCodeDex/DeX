package com.dexstudios.dex.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.R

@Preview(showBackground = true)
@Composable
fun WallpaperLaptopPreview() {
    Image(
        painter = painterResource(id = R.drawable.wallpaper_laptop),
        contentDescription = null,
        modifier = Modifier.size(300.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun WallpaperGamingPreview() {
    Image(
        painter = painterResource(id = R.drawable.wallpaper_gaming),
        contentDescription = null,
        modifier = Modifier.size(300.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun WallpaperServerPreview() {
    Image(
        painter = painterResource(id = R.drawable.wallpaper_server),
        contentDescription = null,
        modifier = Modifier.size(300.dp)
    )
}
