package com.dexstudios.dex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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

    val totalWidth = 320.dp
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
                        }
                    }
                }
            }
    ) {
        val density = LocalDensity.current
        val itemWidth = totalWidth / items.size

        // 1. The Captured Layer (Board + Icons)
        // This Box is drawn first and captured into localNavBackdrop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(localNavBackdrop)
        ) {
            // 1a. The Navbar Board (Backdrop Layer)
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

            // 1b. The Icons Layer
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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

        // 2. The Highlighter Layer (Drawn on top, refracts the layer below)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .graphicsLayer { clip = false }
        ) {
            // Target Bounds Calculation
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

            // Elastic Animations
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

            // Liquid Wobble & Growth
            val infiniteTransition = rememberInfiniteTransition(label = "navWobble")
            val wobbleFactor by infiniteTransition.animateFloat(
                initialValue = -1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "navWobbleFactor"
            )

            val activeWobble by animateFloatAsState(
                targetValue = if (isInteracting) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "activeWobble"
            )

            val indicatorScaleX by animateFloatAsState(
                targetValue = if (isInteracting) 1.05f else 0.8f,
                animationSpec = spring(
                    dampingRatio = if (isInteracting) Spring.DampingRatioMediumBouncy else Spring.DampingRatioHighBouncy,
                    stiffness = if (isInteracting) Spring.StiffnessLow else Spring.StiffnessMedium
                ),
                label = "navIndicatorScaleX"
            )

            val indicatorScaleY by animateFloatAsState(
                targetValue = if (isInteracting) 1.25f else 1.0f,
                animationSpec = spring(
                    dampingRatio = if (isInteracting) Spring.DampingRatioMediumBouncy else Spring.DampingRatioHighBouncy,
                    stiffness = if (isInteracting) Spring.StiffnessLow else Spring.StiffnessMedium
                ),
                label = "navIndicatorScaleY"
            )

            // 2. Shared Animated Highlighter (Topmost Layer)
            val indicatorModifier = Modifier
                .offset { IntOffset(leftBound.roundToPx(), 0) }
                .width(rightBound - leftBound)
                .graphicsLayer {
                    scaleX = indicatorScaleX
                    scaleY = indicatorScaleY
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                    clip = false // Allow liquid bulge outside bounds
                }
                .fillMaxHeight()
                .zIndex(10f)

            LiquidGlassPanel(
                backdrop = localNavBackdrop, // Sample the captured board + icons
                modifier = indicatorModifier,
                shape = CircleShape,
                config = LiquidGlassPresets.IconButton.copy(
                    blurRadius = 1.dp, // Maximum icon clarity
                    lensHeight = 64.dp + (8.dp * wobbleFactor * activeWobble),
                    lensAmount = 35.dp + (15.dp * wobbleFactor * activeWobble),
                    chromaticAberration = false,
                    surfaceTint = MaterialTheme.colorScheme.primary,
                    surfaceTintAlpha = 0.15f,
                    shadowRadius = 0.dp,
                    restRefraction = 1.0f
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
