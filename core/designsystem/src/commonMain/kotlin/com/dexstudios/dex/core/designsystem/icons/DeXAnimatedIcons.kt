package com.dexstudios.dex.core.designsystem.icons

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.painterResource

/**
 * In-memory cache for Lottie JSON strings to avoid repeated disk/resource reads on toggle.
 */
internal object LottieAssetCache {
    private val cache = mutableMapOf<String, String>()

    suspend fun loadJson(path: String): String = cache[path] ?: run {
        val loaded = Res.readBytes(path).decodeToString()
        cache[path] = loaded
        loaded
    }
}

/**
 * Animated Do Not Disturb (DnD) Bell Icon.
 *
 * Automatically switches and animates between:
 * - [isDndActive] = true: Plays v2 Bell-Off slash trim animation, then settles into <AlertOnFilled /> (white in darkmode).
 * - [isDndActive] = false: Plays v4 Active notification bell ringing / swaying oscillation.
 *
 * @param isDndActive Current state of Do Not Disturb.
 * @param modifier Custom Modifier for layout, sizing, or alignment.
 * @param size Target square dimensions (defaults to 24dp).
 * @param tint Color to apply to the icon strokes; defaults to current theme [onSurface].
 * @param contentDescription Accessibility description for the bell icon.
 */
@Composable
fun AnimatedDndBell(isDndActive: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = MaterialTheme.colorScheme.onSurface, contentDescription: String? = "Do Not Disturb") {
    var dndOnJson by remember { mutableStateOf<String?>(null) }
    var dndOffJson by remember { mutableStateOf<String?>(null) }

    // Preload both assets asynchronously
    LaunchedEffect(Unit) {
        try {
            dndOnJson = LottieAssetCache.loadJson("files/bell_dnd_on.json")
            dndOffJson = LottieAssetCache.loadJson("files/bell_dnd_off.json")
        } catch (e: Exception) {
            Logger.e(tag = "DeXAnimatedIcons", throwable = e) { "Failed to load DND bell Lottie assets" }
        }
    }

    val activeJson = if (isDndActive) dndOnJson else dndOffJson

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (activeJson != null) {
            val composition by rememberLottieComposition(isDndActive) {
                LottieCompositionSpec.JsonString(activeJson)
            }

            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = 1,
                isPlaying = true,
                restartOnPlay = true,
            )

            // When notifications are active (DnD is OFF) and v4 finishes its ringing sway, transition to AlertFilled
            val showFilledAlert = (!isDndActive) && (progress >= 0.95f)

            Crossfade(
                targetState = showFilledAlert,
                animationSpec = tween(150),
                label = "AlertFilledCrossfade",
            ) { isFilled ->
                if (isFilled) {
                    Icon(
                        painter = painterResource(DeXIcons.AlertFilled),
                        contentDescription = contentDescription,
                        tint = tint,
                        modifier = Modifier.size(size),
                    )
                } else {
                    val colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null

                    Image(
                        painter = rememberLottiePainter(
                            composition = composition,
                            progress = { progress },
                        ),
                        contentDescription = contentDescription,
                        modifier = Modifier.size(size),
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                    )
                }
            }
        }
    }
}

/**
 * Animated / Kinematic Clipboard Icon.
 *
 * Smoothly crossfades between:
 * - [isClipboardActive] = true: `<ClipboardCheckmarkRegular />`
 * - [isClipboardActive] = false: `<ClipboardOffRegular />`
 *
 * @param isClipboardActive Current state of clipboard synchronization.
 * @param modifier Custom Modifier for layout, sizing, or alignment.
 * @param size Target square dimensions (defaults to 24dp).
 * @param tint Color to apply to the icon strokes; defaults to current theme [onSurface].
 * @param contentDescription Accessibility description for the clipboard icon.
 */
@Composable
fun AnimatedClipboardIcon(
    isClipboardActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = "Clipboard Sync",
) {
    val iconResource = if (isClipboardActive) DeXIcons.ClipboardCheckmark else DeXIcons.ClipboardOff

    Crossfade(
        targetState = iconResource,
        animationSpec = tween(200),
        modifier = modifier.size(size),
        label = "ClipboardStateCrossfade",
    ) { res ->
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(res),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * Animated Search-to-X Morphing Icon.
 *
 * Smoothly morphs between:
 * - [isSearching] = false: Search magnifying glass icon
 * - [isSearching] = true: 'X' close / clear icon
 *
 * When [isSearching] is true and [onClick] is provided, clicking the 'X' triggers [onClick] (e.g. to clear the query).
 *
 * @param isSearching Whether a search query is active (user typed text into the box).
 * @param modifier Custom Modifier for layout, sizing, or alignment.
 * @param size Target square dimensions (defaults to 16dp).
 * @param tint Color to apply to the icon strokes; defaults to current theme [onSurfaceVariant].
 * @param onClick Optional callback invoked when clicked. Active when [isSearching] is true.
 * @param contentDescription Accessibility description for the icon.
 */
@Composable
fun AnimatedSearchToXIcon(
    isSearching: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = if (isSearching) "Clear search" else "Search",
) {
    var searchJson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            searchJson = LottieAssetCache.loadJson("files/search_to_x.json")
        } catch (e: Exception) {
            Logger.e(tag = "DeXAnimatedIcons", throwable = e) { "Failed to load search_to_x Lottie asset" }
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (isSearching) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "SearchToXProgress",
    )

    val clickModifier = if (onClick != null && isSearching) {
        Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        val json = searchJson
        if (json != null) {
            val composition by rememberLottieComposition(Unit) {
                LottieCompositionSpec.JsonString(json)
            }

            val colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null

            Image(
                painter = rememberLottiePainter(
                    composition = composition,
                    progress = { progress },
                ),
                contentDescription = contentDescription,
                modifier = Modifier.requiredSize(size * 2.13f),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
            )
        } else {
            Icon(
                painter = painterResource(if (isSearching) DeXIcons.Close else DeXIcons.Search),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size),
            )
        }
    }
}
