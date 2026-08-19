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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.collectLatest
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.shinyGlare
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

import androidx.compose.ui.tooling.preview.Preview
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.theme.DeXTheme

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
    debugInteractingIndex: Int? = null,
) {
    val selectedIndex by rememberUpdatedState(items.indexOfFirst { it.isSelected }.coerceAtLeast(0))
    var pressedIndex by remember { mutableStateOf<Int?>(debugInteractingIndex) }
    var dragX by remember { mutableStateOf<Float?>(null) }

    val totalWidth = 320.dp
    val visibleHeight = 72.dp
    val samplingHeight = 170.dp // Extra vertical headroom for lens sampling

    // Local backdrop to capture the navbar board and icons so the highlighter can refract them.
    val localNavBackdrop = rememberLayerBackdrop()

    // Liquid Wobble & Growth State
    val isInteracting = pressedIndex != null || dragX != null

    val containerScale by animateFloatAsState(
        targetValue = if (isInteracting) 1.02f else 1.0f,
        animationSpec = tween(
            durationMillis = 150,
            easing = LinearEasing
        ),
        label = "navContainerScale"
    )
    val boardShadowRadius by animateDpAsState(
        targetValue = if (isInteracting) 24.dp else 12.dp,
        animationSpec = tween(
            durationMillis = 150,
            easing = LinearEasing
        ),
        label = "navBoardShadow"
    )

    Box(
        modifier = modifier
            .size(totalWidth, visibleHeight)
            .graphicsLayer {
                clip = false
                scaleX = containerScale
                scaleY = containerScale
            } // Top-level container allows bulging and raising
            .pointerInput(items.size) {
                // Only activate dragX after the finger moves beyond touch slop.
                // This differentiates a TAP (dragX stays null → highlighter follows
                // selectedIndex cleanly) from a DRAG (dragX follows finger).
                val touchSlopPx = viewConfiguration.touchSlop
                awaitPointerEventScope {
                    var startX = 0f
                    var wasTouching = false
                    var dragActivated = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue

                        if (change.pressed) {
                            if (!wasTouching) {
                                // New touch begins — record start position
                                startX = change.position.x
                                dragActivated = false
                            }
                            wasTouching = true

                            if (!dragActivated &&
                                kotlin.math.abs(change.position.x - startX) > touchSlopPx
                            ) {
                                dragActivated = true
                            }

                            if (dragActivated) {
                                dragX = change.position.x
                            }
                        } else {
                            // Finger lifted
                            wasTouching = false
                            dragX = null
                            dragActivated = false
                        }
                    }
                }
            }
    ) {
        val density = LocalDensity.current
        val horizontalPadding = 1.dp
        val availableWidth = totalWidth - (horizontalPadding * 2)
        val itemWidth = availableWidth / items.size

        // The Oversampled Sampling Area Box (Centered on the visible slot)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(totalWidth, samplingHeight)
                .graphicsLayer { clip = false }
        ) {
            // 1. The Captured Layer (Board + Icons)
            // This Box is drawn first and captured into localNavBackdrop.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(localNavBackdrop),
                contentAlignment = Alignment.Center
            ) {
                // 1a. The Navbar Board (Backdrop Layer)
                if (backdrop != null) {
                    LiquidGlassPanel(
                        backdrop = backdrop,
                        modifier = Modifier.size(totalWidth, visibleHeight),
                        shape = config.shape,
                        config = config.copy(
                            shadowRadius = boardShadowRadius,
                            surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                            surfaceTintAlpha = 0.8f
                        ),
                        content = {}
                    )
                } else {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.size(totalWidth, visibleHeight),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = boardShadowRadius,
                        content = {}
                    )
                }

                // 1b. The Icons Layer
                Row(
                    modifier = Modifier
                        .size(totalWidth, visibleHeight)
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                )

                {
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
                    .graphicsLayer { clip = false },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = false }
                ) {
                    // Target Bounds Calculation
                    val targetCenterDp by remember {
                        derivedStateOf {
                            val left = if (dragX != null) {
                                val centerDp = with(density) { dragX!!.toDp() } - horizontalPadding
                                centerDp - (itemWidth / 2f)
                            } else {
                                itemWidth * selectedIndex
                            }
                            left + (itemWidth / 2f)
                        }
                    }

                    val centerX = remember { Animatable(targetCenterDp.value) }

                    LaunchedEffect(Unit) {
                        snapshotFlow {
                            // Read snapshot-observable state INSIDE the flow
                            // so it re-emits whenever dragX or selectedIndex changes.
                            Pair(targetCenterDp.value, dragX != null)
                        }.collectLatest { (target, dragging) ->
                            if (dragging) {
                                // Instant tracking — no spring = no velocity overshoot/ghost
                                centerX.snapTo(target)
                            } else {
                                // Bouncy spring for tab switching and release snap-back.
                                centerX.animateTo(
                                    targetValue = target,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    }

                    // 1.5. Movement Detection
                    val isMoving = centerX.isRunning || kotlin.math.abs(centerX.value - targetCenterDp.value) > 0.5f
                    val isHighlighterActive = isInteracting || isMoving

                    // Velocity-based Stretching (Elongation) and Squashing
                    // We calculate stretch and squash factors based on the current animation velocity
                    val velocity = centerX.velocity
                    val stretchFactor = 0.08f // Increased for more elasticity
                    val squashFactor = 0.03f  // Vertical compression
                    val currentStretch = (Math.abs(velocity) * stretchFactor).coerceAtMost(48f)
                    val currentSquash = (Math.abs(velocity) * squashFactor).coerceAtMost(12f)

                    // Physical Scaling Animations (Replacing graphicsLayer scales)
                    // These targets match the 1.2x width and 1.15x/1.45x height visual style you liked
                    val physicalWidth by animateDpAsState(
                        targetValue = if (isInteracting) 105.dp else 98.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioHighBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "navPhysicalWidth"
                    )

                    val physicalHeight by animateDpAsState(
                        targetValue = if (isInteracting) 78.dp else 62.4.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioHighBouncy,
                            stiffness = if (isInteracting) Spring.StiffnessLow else Spring.StiffnessMedium
                        ),
                        label = "navPhysicalHeight"
                    )

                    // Dynamic Highlighter Visuals - now driven by isHighlighterActive
                    // Note: lens is ONLY active when isInteracting is true to ensure icons stay sharp during tab tap transitions
                    val animatedLensHeight by animateDpAsState(
                        targetValue = if (isInteracting) 32.dp else 0.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "navLensHeight"
                    )
                    val animatedLensAmount by animateDpAsState(
                        targetValue = if (isInteracting) 34.dp else 0.dp,
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
                        targetValue = if (isHighlighterActive) 0.56f else 0.25f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "navRestRefraction"
                    )
                    val animatedHighlightAlpha by animateFloatAsState(
                        targetValue = if (isHighlighterActive) 0.75f else 0.4f,
                        animationSpec = tween(300),
                        label = "navHighlightAlpha"
                    )

                    val animatedShadowRadius by animateDpAsState(
                        targetValue = if (isInteracting) 12.dp else 0.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "navShadowRadius"
                    )

                    val animatedZ by animateDpAsState(
                        targetValue = if (isInteracting) 8.dp else 0.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "navZ"
                    )

                    // 2. Shared Animated Highlighter (Topmost Layer)
                    // The width stretches and height squashes based on movement velocity
                    val indicatorWidth = physicalWidth + currentStretch.dp
                    val indicatorHeight = (physicalHeight - currentSquash.dp).coerceAtLeast(40.dp)
                    // The center remains consistent with the animated centerX
                    val indicatorOffset = horizontalPadding + centerX.value.dp - (indicatorWidth / 2f)

                    val indicatorModifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(indicatorWidth)
                        .height(indicatorHeight)
                        .graphicsLayer {
                            translationX = indicatorOffset.toPx()
                            // Graphics scale removed to prevent coordinate distortion (icon shifting)
                            scaleX = 1.0f
                            scaleY = 1.0f
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                            clip = false // Allow liquid bulge outside bounds
                        }
                        .shinyGlare(
                            shape = CircleShape,
                            width = 2.dp,
                            intensity = if (isHighlighterActive) 1.0f else 0.6f
                        )
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
                            shadowRadius = animatedShadowRadius,
                            shadowOffset = DpOffset(0.dp, animatedShadowRadius / 2f),
                            restRefraction = animatedRestRefraction,
                            depthEffect = true,
                            highlight = LiquidGlassPresets.IconButton.highlight.copy(alpha = animatedHighlightAlpha)
                        ),
                        content = {}
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun FloatingPillNavBarPreview() {
    val items = listOf(
        NavBarItem(MaterialSymbols.GridView, MaterialSymbols.GridView, "Apps", true, {}),
        NavBarItem(MaterialSymbols.History, MaterialSymbols.History, "Recent", false, {}),
        NavBarItem(MaterialSymbols.Notifications, MaterialSymbols.Notifications, "Notifications", false, {}),
        NavBarItem(MaterialSymbols.AccountCircle, MaterialSymbols.AccountCircle, "Profile", false, {})
    )
    DeXTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            Text("Rest State", color = MaterialTheme.colorScheme.onBackground)
            FloatingPillNavBar(items = items)

            Text("Interacting State (Bulge)", color = MaterialTheme.colorScheme.onBackground)
            FloatingPillNavBar(items = items, debugInteractingIndex = 1)
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
            .bubbleFluidity(pullFactor = 0f) // Pull disabled — the highlighter provides drag feedback
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = item.onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = currentIcon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = item.contentDescription,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
