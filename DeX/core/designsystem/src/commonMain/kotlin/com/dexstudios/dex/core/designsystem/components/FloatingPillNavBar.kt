package com.dexstudios.dex.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassConfig
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassPanel
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassPresets
import com.kyant.backdrop.Backdrop

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
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var dragX by remember { mutableStateOf<Float?>(null) }

    val content: @Composable BoxScope.() -> Unit = {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .pointerInput(items.size) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                dragX = if (change.pressed) {
                                    change.position.x
                                } else {
                                    null
                                }
                            }
                        }
                    }
                }
        ) {
            val density = LocalDensity.current
            val totalWidth = maxWidth
            val itemWidth = totalWidth / items.size

            // 1. Target Bounds Calculation
            val targetLeft by remember(dragX, selectedIndex, itemWidth) {
                derivedStateOf {
                    if (dragX != null) {
                        val centerDp = with(density) { dragX!!.toDp() }
                        centerDp - (itemWidth / 2f)
                    } else {
                        itemWidth * selectedIndex
                    }
                }
            }
            val targetRight = targetLeft + itemWidth

            // 2. Elastic Animations
            var lastTargetLeft by remember { mutableStateOf(targetLeft) }
            var direction by remember { mutableIntStateOf(0) }

            SideEffect {
                if (targetLeft != lastTargetLeft) {
                    direction = if (targetLeft > lastTargetLeft) 1 else -1
                    lastTargetLeft = targetLeft
                }
            }

            val leftBound by animateDpAsState(
                targetValue = targetLeft,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = if (direction <= 0) 1200f else 600f
                ),
                label = "navLeftBound"
            )

            val rightBound by animateDpAsState(
                targetValue = targetRight,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = if (direction >= 0) 1200f else 600f
                ),
                label = "navRightBound"
            )

            val indicatorScale by animateFloatAsState(
                targetValue = if (pressedIndex != null || dragX != null) 0.92f else 1f,
                animationSpec = spring(
                    dampingRatio = if (pressedIndex != null || dragX != null) Spring.DampingRatioMediumBouncy else Spring.DampingRatioHighBouncy,
                    stiffness = if (pressedIndex != null || dragX != null) Spring.StiffnessLow else Spring.StiffnessMedium
                ),
                label = "navIndicatorScale"
            )

            // 1. Shared Animated Highlighter (Liquid Glass or Solid Fallback)
            // Drawn BEFORE the icons to ensure the icons are on top.
            val indicatorModifier = Modifier
                .offset { IntOffset(leftBound.roundToPx(), 0) }
                .width(rightBound - leftBound)
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                }
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
                        surfaceTint = MaterialTheme.colorScheme.primary,
                        surfaceTintAlpha = 0.12f
                    )
                ) { }
            } else {
                Box(
                    modifier = indicatorModifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // 2. Icons Row (Drawn on top of the highlighter)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    NavBarIcon(
                        item = item,
                        onPressedChanged = { isPressed ->
                            pressedIndex = if (isPressed) index else null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    val panelModifier = modifier
        .width(320.dp)
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
    onPressedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onPressedChanged(isPressed)
    }

    // Restore animated visuals based on selection
    val contentColor by animateColorAsState(
        targetValue = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
            fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

