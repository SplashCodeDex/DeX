package com.dexstudios.dex.core.designsystem.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

/**
 * In-memory cache for Lottie JSON strings to avoid repeated disk/resource reads on toggle.
 */
internal object LottieAssetCache {
    private val cache = mutableMapOf<String, String>()

    suspend fun loadJson(path: String): String {
        return cache[path] ?: run {
            val loaded = Res.readBytes(path).decodeToString()
            cache[path] = loaded
            loaded
        }
    }
}

/**
 * Animated Do Not Disturb (DnD) Bell Icon.
 *
 * Automatically switches and animates between:
 * - [isDndActive] = true: v2 Bell-Off trim slash cutting through the bell (muted notification).
 * - [isDndActive] = false: v4 Active notification bell ringing / swaying oscillation.
 *
 * @param isDndActive Current state of Do Not Disturb.
 * @param modifier Custom Modifier for layout, sizing, or alignment.
 * @param size Target square dimensions (defaults to 24dp).
 * @param tint Color to apply to the icon strokes; defaults to current theme [onSurface].
 * @param contentDescription Accessibility description for the bell icon.
 */
@Composable
fun AnimatedDndBell(
    isDndActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = "Do Not Disturb"
) {
    var dndOnJson by remember { mutableStateOf<String?>(null) }
    var dndOffJson by remember { mutableStateOf<String?>(null) }

    // Preload both assets asynchronously
    LaunchedEffect(Unit) {
        try {
            dndOnJson = LottieAssetCache.loadJson("files/bell_dnd_on.json")
            dndOffJson = LottieAssetCache.loadJson("files/bell_dnd_off.json")
        } catch (e: Exception) {
            println("DeXAnimatedIcons: Failed to load DND bell Lottie assets: ${e.message}")
        }
    }

    val activeJson = if (isDndActive) dndOnJson else dndOffJson

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (activeJson != null) {
            val composition by rememberLottieComposition(isDndActive) {
                LottieCompositionSpec.JsonString(activeJson)
            }

            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = 1,
                isPlaying = true,
                restartOnPlay = true
            )

            val colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null

            Image(
                painter = rememberLottiePainter(
                    composition = composition,
                    progress = { progress }
                ),
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter
            )
        }
    }
}
