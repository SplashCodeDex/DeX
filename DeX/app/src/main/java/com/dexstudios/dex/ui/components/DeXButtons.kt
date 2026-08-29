package com.dexstudios.dex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassTokens
import com.dexstudios.dex.ui.components.glass.shinyGlare
import com.kyant.backdrop.Backdrop

// ============================================================================
// DeX Button Wrappers
// These replace standard Material3 buttons to ensure spatial physics are applied globally.
// ============================================================================

@Composable
fun DeXButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.shape,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .bubbleFluidity()
            .shinyGlare(shape = shape),
        enabled = enabled,
        colors = colors,
        shape = shape,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun DeXTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.textShape,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .bubbleFluidity()
            .shinyGlare(shape = shape),
        enabled = enabled,
        colors = colors,
        shape = shape,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Spatial Frosted Liquid Glass Button with optical refraction, specular glare, and bubble physics.
 */
@Composable
fun LiquidGlassButton(
    text: String,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val buttonShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .height(52.dp)
            .bubbleFluidity(targetScale = 1.04f, pullFactor = 0.05f)
            .clip(buttonShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (backdrop != null) {
            LiquidGlassPanel(
                backdrop = backdrop,
                modifier = Modifier
                    .matchParentSize()
                    .shinyGlare(
                        shape = buttonShape,
                        intensity = LiquidGlassTokens.GlareFactor
                    ),
                shape = buttonShape,
                config = LiquidGlassPresets.IconButton.copy(
                    shape = buttonShape,
                    blurRadius = 14.dp,
                    surfaceTint = MaterialTheme.colorScheme.primary,
                    surfaceTintAlpha = 0.28f,
                ),
                content = {}
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
