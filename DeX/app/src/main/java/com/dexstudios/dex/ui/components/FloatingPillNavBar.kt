package com.dexstudios.dex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

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

    val totalWidth = 300.dp
    val totalHeight = 72.dp

    // Local backdrop to capture the navbar board and icons so the highlighter can refract them.
    val localNavBackdrop = rememberLayerBackdrop()

    // Liquid Wobble & Growth State
    val isInteracting = pressedIndex != null || dragX != null

    Box(
        modifier = modifier
            .size(totalWidth, totalHeight)
            .graphicsLayer { clip = false } // Top-level container allows bulging
            .pointerInput(items.size) {
                // Tracking dragX at the root level to avoid hit-test blocking
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
                        } else {
                            dragX = null
                        }
                    }
                }
            }
    ) {
        val density = LocalDensity.current
        val horizontalPadding = 1.dp
        val availableWidth = totalWidth - (horizontalPadding * 2)
        val itemWidth = availableWidth / items.size

        // Target Bounds Calculation
        val targetLeft by remember(dragX, selectedIndex, itemWidth) {
            derivedStateOf {
                if (dragX != null) {
                    val centerDp = with(density) { dragX!!.toDp() } - horizontalPadding
                    centerDp - (itemWidth / 2f)
                } else {
                    itemWidth * selectedIndex
                }
            }
        }
        val targetRight = targetLeft + itemWidth

        val leftBound by animateDpAsState(
            targetValue = targetLeft,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = 600f
            ),
            label = "navLeftBound"
        )

        val rightBound by animateDpAsState(
            targetValue = targetRight,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = 600f
            ),
            label = "navRightBound"
        )

        // Movement Detection
        val isMoving = remember(leftBound, targetLeft, rightBound, targetRight) {
            val tolerance = 0.5f // dp
            Math.abs(leftBound.value - targetLeft.value) > tolerance ||
            Math.abs(rightBound.value - targetRight.value) > tolerance
        }
        val isHighlighterActive = isInteracting || isMoving

        val captureHeight = 96.dp

        // 1. The Captured Layer (Background Cards + Board + Icons)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(captureHeight)
                .align(Alignment.Center)
                .layerBackdrop(localNavBackdrop)
        ) {
            // 1a. Sample the global background cards into this tall capture zone
            // Alpha is driven by interaction to hide borders at rest
            if (backdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (isHighlighterActive) 1f else 0f }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = {
                                blur(0f)
                            }
                        )
                )
            }

            // 1b. The Navbar Board + Icons (Centered in the capture zone)
            Box(
                modifier = Modifier
                    .size(totalWidth, totalHeight)
                    .align(Alignment.Center)
            ) {
                // The Navbar Board
                if (backdrop != null) {
                    LiquidGlassPanel(
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxSize(),
                        shape = config.shape,
                        config = config,
                        content = {}
                    )
                } else {
                    DeXPanel(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        content = {}
                    )
                }

                // The Icons
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = 8.dp),
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

        // 2. The Highlighter Layer (Drawn on top, refracts the layer below)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true)
                .graphicsLayer { clip = false }
        ) {
            // Physical Scaling Animations
            val physicalWidth by animateDpAsState(
                targetValue = if (isInteracting) 92.dp else 86.4.dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "navPhysicalWidth"
            )

            val physicalHeight by animateDpAsState(
                targetValue = if (isHighlighterActive) 96.dp else 64.4.dp,
                animationSpec = spring(
                    dampingRatio = if (isHighlighterActive) Spring.DampingRatioMediumBouncy else Spring.DampingRatioHighBouncy,
                    stiffness = if (isHighlighterActive) Spring.StiffnessLow else Spring.StiffnessMedium
                ),
                label = "navPhysicalHeight"
            )

            // Dynamic Highlighter Visuals - now driven by isHighlighterActive
            val animatedLensHeight by animateDpAsState(
                targetValue = if (isHighlighterActive) 30.dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "navLensHeight"
            )
            val animatedLensAmount by animateDpAsState(
                targetValue = if (isHighlighterActive) 30.dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "navLensAmount"
            )
            val animatedSurfaceTint by animateColorAsState(
                targetValue = if (isHighlighterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(300),
                label = "navSurfaceTint"
            )
            val animatedSurfaceTintAlpha by animateFloatAsState(
                targetValue = if (isHighlighterActive) 0.15f else 0.2f,
                animationSpec = tween(300),
                label = "navSurfaceTintAlpha"
            )

            val animatedRestRefraction by animateFloatAsState(
                targetValue = if (isHighlighterActive) 1.0f else 0.0f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "navRestRefraction"
            )
            val animatedHighlightAlpha by animateFloatAsState(
                targetValue = if (isHighlighterActive) 0.15f else 0.0f,
                animationSpec = tween(300),
                label = "navHighlightAlpha"
            )

            // 2. Shared Animated Highlighter (Topmost Layer)
            // Centering logic for spherical shape
            val currentSlotWidth = rightBound - leftBound
            val indicatorWidth = physicalWidth
            // Offset must include the horizontalPadding to align with the Icons layer
            val indicatorOffset = horizontalPadding + leftBound + (currentSlotWidth - indicatorWidth) / 2

            val indicatorModifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(indicatorWidth)
                .requiredHeight(physicalHeight)
                .graphicsLayer {
                    // Graphics scale removed to prevent coordinate distortion (icon shifting)
                    scaleX = 1.0f
                    scaleY = 1.0f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                    clip = false // Allow liquid bulge outside bounds
                }
                .zIndex(10f)

            // Unified Highlighter: Always a LiquidGlassPanel, but its effects animate to 0 at rest
            LiquidGlassPanel(
                backdrop = localNavBackdrop,
                modifier = indicatorModifier,
                shape = CircleShape,
                config = LiquidGlassPresets.IconButton.copy(
                    blurRadius = 0.dp,
                    lensHeight = animatedLensHeight,
                    lensAmount = animatedLensAmount,
                    chromaticAberration = false,
                    surfaceTint = animatedSurfaceTint,
                    surfaceTintAlpha = animatedSurfaceTintAlpha,
                    shadowRadius = 0.dp,
                    restRefraction = animatedRestRefraction,
                    depthEffect = true,
                    highlight = LiquidGlassPresets.IconButton.highlight.copy(alpha = animatedHighlightAlpha)
                ),
                content = {}
            )
        }
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
