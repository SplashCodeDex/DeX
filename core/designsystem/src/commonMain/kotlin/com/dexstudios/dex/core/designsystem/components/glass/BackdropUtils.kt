package com.dexstudios.dex.core.designsystem.components.glass

import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop

/**
 * CompositionLocal to provide a backdrop instance to nested components.
 * This allows glass buttons to automatically sample the card content
 * behind them for refractive effects without explicit parameter threading.
 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }
