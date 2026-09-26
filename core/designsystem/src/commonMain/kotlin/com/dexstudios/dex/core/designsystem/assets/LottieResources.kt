package com.dexstudios.dex.core.designsystem.assets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.designsystem.generated.resources.Res

/**
 * Single source for every Lottie JSON that ships in `composeResources/files`.
 *
 * These bytes used to exist twice: once here for desktop, and again in the Android app's
 * `src/main/assets/lottie/` for `LottieCompositionSpec.Asset`. Carrying 8.7 MB of animation data
 * in two trees is how two platforms drift apart with nobody noticing, so the JSONs now live in
 * `commonMain/composeResources/files` and both targets read them through [LottieAssetCache].
 *
 * Each constant is the compose-resources path itself, so the name and the file on disk cannot
 * disagree about spelling.
 */
object LottiePaths {
    const val DEVICES_MORPH = "files/DevicesMorph.json"
    const val DEVICE_CONNECTED = "files/device_connected.json"
    const val TABLET_CONNECTED = "files/tablet_connected.json"
    const val LAPTOP_CONNECTED = "files/laptop_connected.json"
    const val WATCH_CONNECTED = "files/watch_connected.json"
    const val BELL_DND_ON = "files/bell_dnd_on.json"
    const val BELL_DND_OFF = "files/bell_dnd_off.json"
    const val SEARCH_TO_X = "files/search_to_x.json"
}

/**
 * Process-wide cache for Lottie JSON *strings*, keyed by [LottiePaths].
 *
 * `Res.readBytes` is suspend IO and these files are megabytes: re-reading one every time an empty
 * state or a carousel card re-enters composition stalled the placeholder it was drawing, so the
 * first caller loads and everyone after reuses.
 */
object LottieAssetCache {
    private val cache = mutableMapOf<String, String>()

    /** Loads [path] and caches it. Throws if the resource is missing. */
    suspend fun loadJson(path: String): String = cache[path] ?: run {
        val loaded = Res.readBytes(path).decodeToString()
        cache[path] = loaded
        loaded
    }

    /**
     * [loadJson] without the throw: a missing or unreadable animation must not take down the
     * screen drawing it. The failure is logged with its path rather than swallowed, so a
     * packaging regression stays visible in the logs.
     */
    suspend fun loadJsonOrNull(path: String): String? = runCatching { loadJson(path) }
        .onFailure { e -> Logger.e(tag = "LottieAssetCache", throwable = e) { "Failed to load $path" } }
        .getOrNull()
}

/**
 * The JSON at [path] as state: `null` until the bytes are in memory, then the string itself.
 *
 * Feed the result to `LottieCompositionSpec.JsonString(json)` — airbnb's on Android, Compottie's
 * on desktop. Neither needs an explicit cache key: both derive one from the JSON content, so
 * every call site rendering the same animation shares a single parsed composition, and the
 * animation starts as soon as the bytes land instead of one frame later on a cold cache.
 */
@Composable
fun rememberLottieJson(path: String): State<String?> = produceState<String?>(initialValue = null, key1 = path) {
    value = LottieAssetCache.loadJsonOrNull(path)
}
