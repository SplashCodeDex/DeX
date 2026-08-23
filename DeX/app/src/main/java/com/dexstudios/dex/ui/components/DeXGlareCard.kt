package com.dexstudios.dex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.ui.components.glass.shinyGlare

/**
 * A standardized, reusable card component that replicates the "Scan" card style.
 * Uses a high-performance shiny glare border and a solid/semi-transparent background
 * instead of the expensive Liquid Glass blur panel.
 */
@Composable
fun DeXGlareCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(48.dp),
    backgroundColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shinyGlare(shape = shape)
            .clip(shape)
    ) {
        // Background layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(backgroundColor ?: MaterialTheme.colorScheme.surfaceVariant)
        )

        // Content
        content()
    }
}
