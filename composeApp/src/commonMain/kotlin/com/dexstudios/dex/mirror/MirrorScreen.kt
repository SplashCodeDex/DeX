package com.dexstudios.dex.mirror

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassPanel
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassPresets
import com.dexstudios.dex.core.designsystem.theme.LocalBackdrop
import com.dexstudios.dex.core.network.IMirrorEngine
import org.koin.compose.koinInject

@Composable
fun MirrorScreen(modifier: Modifier = Modifier, mirrorEngine: IMirrorEngine = koinInject()) {
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val backdrop = LocalBackdrop.current

    DisposableEffect(mirrorEngine) {
        mirrorEngine.frameSender = { bytes ->
            try {
                currentFrame = bytes.toImageBitmap()
            } catch (e: Exception) {
                // Ignore decoding errors
            }
        }
        onDispose {
            mirrorEngine.frameSender = null
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        val panelModifier = Modifier.fillMaxSize()

        if (backdrop != null) {
            LiquidGlassPanel(
                backdrop = backdrop,
                modifier = panelModifier,
                config = LiquidGlassPresets.Frosted,
            ) {
                FrameContent(currentFrame)
            }
        } else {
            Box(modifier = panelModifier) {
                FrameContent(currentFrame)
            }
        }
    }
}

@Composable
private fun FrameContent(currentFrame: ImageBitmap?) {
    if (currentFrame != null) {
        Image(
            bitmap = currentFrame,
            contentDescription = "Screen Mirror",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Text("Waiting for connection...", modifier = Modifier.padding(16.dp))
    }
}
