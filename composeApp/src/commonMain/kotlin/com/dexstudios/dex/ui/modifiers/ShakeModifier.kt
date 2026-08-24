package com.dexstudios.dex.ui.modifiers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

fun Modifier.shake(isError: Boolean): Modifier = composed {
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(isError) {
        if (isError) {
            shakeAnim.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    (-10f) at 60
                    10f at 120
                    (-10f) at 180
                    10f at 240
                    0f at 400
                },
            )
        }
    }
    this.offset { IntOffset(x = shakeAnim.value.roundToInt(), y = 0) }
}
