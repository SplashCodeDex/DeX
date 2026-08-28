package com.dexstudios.dex.window.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Skeleton placeholders for the explorer grid while a listing is being computed.
 *
 * Motion discipline: a single shared alpha pulse drives every bone, so the whole grid
 * breathes as one surface instead of N competing animators; the transition only exists
 * while skeletons are composed (loading && no stale content), so it costs zero frames
 * the moment real content lands. Flat theme tones with alpha - deliberately no gradient
 * shimmer sweep, matching the app's no-gradient design rules.
 */
@Composable
internal fun ExplorerSkeletonGrid(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "skeletonPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val tone = MaterialTheme.colorScheme.surfaceVariant

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // Mirrors the real grid's padding so content arrival never shifts layout.
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(SkeletonBoneCount) { index ->
            SkeletonFileCard(tone = tone, pulseAlpha = pulse, seed = index)
        }
    }
}

private const val SkeletonBoneCount = 12

/** 100x118dp placeholder mirroring FileGridItemCard: glyph box + two text lines. */
@Composable
private fun SkeletonFileCard(tone: Color, pulseAlpha: Float, seed: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tone.copy(alpha = pulseAlpha)),
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Widths vary deterministically per row position so the wall of bones
            // does not read as one cloned rectangle.
            SkeletonLine(widthFraction = if (seed % 3 == 0) 0.82f else 0.62f, heightDp = 9, tone = tone, pulseAlpha = pulseAlpha)
            Spacer(modifier = Modifier.height(3.dp))
            SkeletonLine(widthFraction = if (seed % 2 == 0) 0.45f else 0.34f, heightDp = 7, tone = tone, pulseAlpha = pulseAlpha)
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, heightDp: Int, tone: Color, pulseAlpha: Float) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier
                .width((64.dp * widthFraction))
                .height(heightDp.dp)
                .clip(RoundedCornerShape(heightDp.dp / 2))
                .background(tone.copy(alpha = pulseAlpha * 0.85f)),
        )
    }
}
