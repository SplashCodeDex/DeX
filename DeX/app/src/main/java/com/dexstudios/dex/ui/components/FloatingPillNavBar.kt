package com.dexstudios.dex.ui.components

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
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
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
                                if (change.pressed) {
                                    dragX = change.position.x
                                } else {
                                    dragX = null
                                }
                            }
                        }
                    }
                }
        ) {
            val itemWidth = maxWidth / items.size

            // 1. Target Bounds Calculation
            // If dragging, center the blob on the finger.
            // If not dragging but pressing, peek slightly (handled by the drag logic if we treat press as dragX).
            // Otherwise, target the selected index.
            val targetLeft: Dp
            val targetRight: Dp

            if (dragX != null) {
                val centerDp = with(LocalDensity.current) { dragX!!.toDp() }
                // During drag, we stretch the blob slightly if it's far from the center of an item?
                // For now, let's just make it follow the finger with a fixed width,
                // and the "stretching" will come from the independent springs.
                targetLeft = centerDp - (itemWidth / 2f)
                targetRight = centerDp + (itemWidth / 2f)
            } else {
                targetLeft = itemWidth * selectedIndex
                targetRight = itemWidth * (selectedIndex + 1)
            }

            // 2. Elastic Animations
            // The "leading" side should be stiffer, "trailing" side lazier to create the stretch.
            var lastTargetLeft by remember { mutableStateOf(targetLeft) }
            var direction by remember { mutableIntStateOf(0) } // 1 for right, -1 for left

            if (targetLeft != lastTargetLeft) {
                direction = if (targetLeft > lastTargetLeft) 1 else -1
                lastTargetLeft = targetLeft
            }

            val leftBound by animateDpAsState(
                targetValue = targetLeft,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = if (direction <= 0) Spring.StiffnessMedium else Spring.StiffnessLow
                ),
                label = "navLeftBound"
            )

            val rightBound by animateDpAsState(
                targetValue = targetRight,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = if (direction >= 0) Spring.StiffnessMedium else Spring.StiffnessLow
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

            // 3. Shared Animated Highlighter (Liquid Glass or Solid Fallback)
            val indicatorModifier = Modifier
                .offset { IntOffset(leftBound.roundToPx(), 0) }
                .width(rightBound - leftBound)
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                    // Ensure the scale happens from the center of the current width
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

            // 2. Icons Row
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    NavBarIcon(
                        item = item,
                        isGlass = backdrop != null,
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
    isGlass: Boolean,
    onPressedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onPressedChanged(isPressed)
    }

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
