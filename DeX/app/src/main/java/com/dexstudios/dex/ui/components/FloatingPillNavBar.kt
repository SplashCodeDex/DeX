package com.dexstudios.dex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.kyant.backdrop.Backdrop
import androidx.compose.ui.graphics.isSpecified

data class NavBarItem(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String,
    val isSelected: Boolean,
    val onClick: () -> Unit
)

@Composable
fun FloatingPillNavBar(
    items: List<NavBarItem>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    config: LiquidGlassConfig = LiquidGlassPresets.NavBar,
) {
    val selectedIndex = items.indexOfFirst { it.isSelected }.coerceAtLeast(0)

    val content: @Composable BoxScope.() -> Unit = {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            val itemWidth = maxWidth / items.size
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "navIndicatorOffset"
            )

            // 1. Shared Animated Highlighter (Liquid Glass or Solid Fallback)
            val indicatorModifier = Modifier
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(itemWidth)
                .fillMaxHeight()

            if (backdrop != null) {
                LiquidGlassPanel(
                    backdrop = backdrop,
                    modifier = indicatorModifier,
                    shape = CircleShape,
                    config = LiquidGlassPresets.IconButton.copy(
                        blurRadius = 4.dp,
                        lensHeight = 12.dp,
                        lensAmount = 24.dp,
                        surfaceTintAlpha = 0.15f
                    )
                ) { }
            } else {
                Box(
                    modifier = indicatorModifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // 2. Icons Row
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    NavBarIcon(
                        item = item,
                        isGlass = backdrop != null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    val panelModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .height(72.dp)

    if (backdrop != null) {
        LiquidGlassPanel(
            backdrop = backdrop,
            modifier = panelModifier,
            shape = config.shape,
            config = config,
            content = content
        )
    } else {
        DeXPanel(
            modifier = panelModifier,
            shape = CircleShape,
            content = content
        )
    }
}

@Composable
private fun NavBarIcon(
    item: NavBarItem,
    isGlass: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // When using glass, we use the primary color for the "selected" state to pop through the lens.
    // When using a solid background, we use onPrimary for contrast.
    val targetContentColor = if (item.isSelected) {
        if (isGlass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        label = "navContentColor"
    )
    val currentIcon = if (item.isSelected) item.selectedIcon else item.unselectedIcon

    Column(
        modifier = modifier
            .bubbleFluidity()
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = item.onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = currentIcon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp).padding(bottom = 2.dp)
        )
        Text(
            text = item.contentDescription,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
