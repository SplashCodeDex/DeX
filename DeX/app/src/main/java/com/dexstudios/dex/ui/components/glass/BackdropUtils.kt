package com.dexstudios.dex.ui.components.glass

import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop

/**
 * CompositionLocal to provide a backdrop instance to nested components.
 * This allows components like [com.dexstudios.dex.ui.components.DeXButton] to
 * automatically sample the background for refractive glass effects.
 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }
