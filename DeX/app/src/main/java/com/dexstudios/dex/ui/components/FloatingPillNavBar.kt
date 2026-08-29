package com.dexstudios.dex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.R
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassTokens
import com.dexstudios.dex.ui.components.glass.shinyGlare
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.theme.DeXTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest

data class NavBarItem(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun FloatingPillNavBar(
    items: List<NavBarItem>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    config: LiquidGlassConfig = LiquidGlassPresets.NavBar,
    lensHeight: Dp = 190.dp,
    lensAmount: Dp = 100.dp,
    restRefraction: Float = 0.11f,
) {
    val selectedIndex by rememberUpdatedState(items.indexOfFirst { it.isSelected }.coerceAtLeast(0))
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var dragX by remember { mutableStateOf<Float?>(null) }
    val isInteracting = pressedIndex != null || dragX != null

    val totalWidth = 320.dp
    val visibleHeight = 72.dp
    val samplingHeight = 170.dp

    val density = LocalDensity.current

    val horizontalPadding = 1.dp
    val availableWidth = totalWidth - (horizontalPadding * 2)
    val itemWidth = availableWidth / items.size

    val selectedCenterDp = horizontalPadding + (itemWidth * selectedIndex) + (itemWidth / 2f)
    val isPeaking = pressedIndex != null && pressedIndex != selectedIndex

    // Add a directional peak shift towards the pressed tab
    val peakShiftDp =
        if (isPeaking) {
            val pressedCenterDp =
                horizontalPadding + (itemWidth * pressedIndex!!) + (itemWidth / 2f)
            val diff = pressedCenterDp - selectedCenterDp
            if (diff > 0.dp) 20.dp else -20.dp
        } else 0.dp

    // Stretch width to create a teardrop shape pointing towards the finger
    val peakStretchDp = if (isPeaking) 24.dp else 0.dp

    // --- Highlighter dynamic sizing on interact ---
    val highlighterWidth by
        animateDpAsState(
            targetValue = (if (isInteracting) 140.dp else 98.dp) + peakStretchDp,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
            label = "hlW",
        )
    val highlighterHeight by
        animateDpAsState(
            targetValue = if (isInteracting) 90.dp else 62.dp,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
            label = "hlH",
        )

    // --- Dynamic lens & refraction warp on interact ---
    val animatedLensHeight by
        animateDpAsState(
            targetValue = if (isInteracting) lensHeight else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "lensH",
        )
    val animatedLensAmount by
        animateDpAsState(
            targetValue = if (isInteracting) lensAmount else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "lensA",
        )
    val animatedRefraction by
        animateFloatAsState(
            targetValue = if (isInteracting) restRefraction else 0.20f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "refr",
        )

    // --- Elevation transition on interact ---
    val animatedShadow by
        animateDpAsState(
            targetValue = if (isInteracting) 30.dp else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "shadow",
        )

    // --- Dynamic blur on interact ---
    val animatedBlur by
        animateDpAsState(
            targetValue = if (isInteracting) 0.50.dp else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "blur",
        )

    // --- Inner shadow on interact ---
    val animatedInnerShadowRadius by
        animateDpAsState(
            targetValue = if (isInteracting) 12.dp else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "innerShadowR",
        )
    val animatedInnerShadowAlpha by
        animateFloatAsState(
            targetValue = if (isInteracting) 0.45f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "innerShadowA",
        )

    // --- Highlighter position: drag follows finger, otherwise follows selected tab (with peak
    // offset) ---
    val targetCenterDp by remember {
        derivedStateOf {
            if (dragX != null) {
                with(density) { dragX!!.toDp() }
            } else {
                val selectedCenter =
                    horizontalPadding + (itemWidth * selectedIndex) + (itemWidth / 2f)
                val isPeaking = pressedIndex != null && pressedIndex != selectedIndex

                val shift =
                    if (isPeaking) {
                        val pressedCenter =
                            horizontalPadding + (itemWidth * pressedIndex!!) + (itemWidth / 2f)
                        val diff = pressedCenter - selectedCenter
                        if (diff > 0.dp) 20.dp else -20.dp
                    } else 0.dp

                selectedCenter + shift
            }
        }
    }

    val centerX = remember { Animatable(with(targetCenterDp) { value }) }

    LaunchedEffect(Unit) {
        snapshotFlow { Pair(targetCenterDp.value, dragX != null) }
            .collectLatest { (target, dragging) ->
                if (dragging) {
                    centerX.snapTo(target)
                } else {
                    centerX.animateTo(
                        targetValue = target,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f),
                    )
                }
            }
    }

    // --- Tint transitions: active while interacting OR still springing ---
    val isMoving = centerX.isRunning || kotlin.math.abs(centerX.value - targetCenterDp.value) > 0.5f
    val isHighlighterActive = isInteracting || isMoving

    val animatedTint by
        animateColorAsState(
            targetValue =
                if (isHighlighterActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(300),
            label = "tint",
        )
    val animatedTintAlpha by
        animateFloatAsState(
            targetValue = if (isHighlighterActive) 0.15f else 0.2f,
            animationSpec = tween(300),
            label = "tintA",
        )

    val localNavBackdrop = rememberLayerBackdrop()

    // We will extract the exact physics values from bubbleFluidity so we can inverse-scale the base
    // layer
    var currentScale by remember { mutableFloatStateOf(1f) }
    var currentTx by remember { mutableFloatStateOf(0f) }
    var currentTy by remember { mutableFloatStateOf(0f) }

    Box(
        modifier =
            modifier
                .size(totalWidth + 16.dp, visibleHeight + 12.dp)
                .bubbleFluidity(
                    targetScale = 0.95f,
                    pullFactor = 0.10f,
                    onPhysicsUpdated = { s, tx, ty ->
                        currentScale = s
                        currentTx = tx
                        currentTy = ty
                    },
                )
                .pointerInput(items.size) {
                    val touchSlopPx = viewConfiguration.touchSlop
                    awaitPointerEventScope {
                        var startX = 0f
                        var wasTouching = false
                        var dragActivated = false
                        var startedOnHighlighter = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue

                            if (change.pressed) {
                                if (!wasTouching) {
                                    startX = change.position.x
                                    dragActivated = false

                                    // Check if touch started on the highlighter
                                    val currentCenterPx = (centerX.value.dp + 8.dp).toPx()
                                    val widthPx = highlighterWidth.toPx()
                                    val touchPadding = 16.dp.toPx()
                                    val leftBound = currentCenterPx - (widthPx / 2f) - touchPadding
                                    val rightBound = currentCenterPx + (widthPx / 2f) + touchPadding
                                    startedOnHighlighter = startX in leftBound..rightBound
                                }
                                wasTouching = true

                                if (
                                    startedOnHighlighter &&
                                        !dragActivated &&
                                        abs(change.position.x - startX) > touchSlopPx
                                ) {
                                    dragActivated = true
                                }

                                if (dragActivated) {
                                    dragX = change.position.x - 8.dp.toPx()
                                }
                            } else {
                                if (dragActivated && dragX != null) {
                                    val itemWidthPx = itemWidth.toPx()
                                    val dropIndex =
                                        (dragX!! / itemWidthPx).toInt().coerceIn(0, items.size - 1)
                                    if (dropIndex != selectedIndex) {
                                        items[dropIndex].onClick()
                                    }
                                }
                                wasTouching = false
                                dragX = null
                                dragActivated = false
                                startedOnHighlighter = false
                            }
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // 1. CAPTURED LAYER (Base Layer + Board + Icons)
        Box(
            modifier =
                Modifier.requiredSize(totalWidth + 16.dp, samplingHeight)
                    .graphicsLayer { clip = false }
                    .layerBackdrop(localNavBackdrop),
            contentAlignment = Alignment.Center,
        ) {
            // STATIC Base Layer: Inverse-scaled to perfectly cancel out the outer bubbleFluidity!
            // This ensures drawBackdrop fetches the exact un-squished bounds from the screen,
            // preventing the "zoom out" artifact!
            if (backdrop != null) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                val invScale = if (currentScale > 0f) 1f / currentScale else 1f
                                scaleX = invScale
                                scaleY = invScale
                                translationX = -currentTx
                                translationY = -currentTy
                            }
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                effects = {},
                                highlight = { null },
                                shadow = { null },
                                innerShadow = { null },
                                onDrawSurface = {},
                            )
                )
            }

            // Board
            if (backdrop != null) {
                LiquidGlassPanel(
                    backdrop = backdrop,
                    modifier = Modifier.size(totalWidth, visibleHeight),
                    shape = config.shape,
                    config =
                        config.copy(
                            surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                            surfaceTintAlpha = 0.8f,
                        ),
                    content = {},
                )
            } else {
                androidx.compose.material3.Surface(
                    modifier = Modifier.size(totalWidth, visibleHeight),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 12.dp,
                    content = {},
                )
            }

            // Icons
            Row(
                modifier =
                    Modifier.size(totalWidth, visibleHeight)
                        .padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    NavBarIcon(
                        item = item,
                        onPressedChanged = { isPressed ->
                            pressedIndex = if (isPressed) index else null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 2. HIGHLIGHTER (Draws on top, samples the captured layer, refracting both board and
        // icons)
        val indicatorOffset = centerX.value.dp - (highlighterWidth / 2f) + 8.dp

        Box(
            modifier =
                Modifier.requiredSize(totalWidth + 16.dp, samplingHeight).graphicsLayer {
                    clip = false
                }
        ) {
            LiquidGlassPanel(
                backdrop = localNavBackdrop,
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .size(highlighterWidth, highlighterHeight)
                        .graphicsLayer {
                            translationX = indicatorOffset.toPx()
                            clip = false
                        }
                        .shinyGlare(
                            shape = RoundedCornerShape(40.dp),
                            intensity = LiquidGlassTokens.GlareRestAlpha,
                        )
                        .zIndex(10f),
                shape = RoundedCornerShape(40.dp),
                config =
                    LiquidGlassPresets.IconButton.copy(
                        blurRadius = animatedBlur,
                        lensHeight = animatedLensHeight,
                        lensAmount = animatedLensAmount,
                        surfaceTint = animatedTint,
                        surfaceTintAlpha = animatedTintAlpha,
                        restRefraction = animatedRefraction,
                        shadowRadius = animatedShadow,
                        depthEffect = true,
                        highlight =
                            LiquidGlassPresets.IconButton.highlight.copy(
                                alpha = LiquidGlassTokens.GlareRestAlpha
                            ),
                        innerShadow =
                            com.kyant.backdrop.shadow.InnerShadow(
                                radius = animatedInnerShadowRadius,
                                color = Color.Black.copy(alpha = animatedInnerShadowAlpha),
                                offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp),
                            ),
                    ),
                content = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun FloatingPillNavBarPreview() {
    val items =
        listOf(
            NavBarItem(MaterialSymbols.GridView, MaterialSymbols.GridView, "Apps", true, {}),
            NavBarItem(MaterialSymbols.History, MaterialSymbols.History, "Recent", false, {}),
            NavBarItem(
                MaterialSymbols.Notifications,
                MaterialSymbols.Notifications,
                "Notifications",
                false,
                {},
            ),
            NavBarItem(
                MaterialSymbols.AccountCircle,
                MaterialSymbols.AccountCircle,
                "Profile",
                false,
                {},
            ),
        )
    val backdrop = rememberLayerBackdrop()

    DeXTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.wallpaper_laptop),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentScale = ContentScale.Crop,
            )

            Column(
                modifier = Modifier.padding(20.dp).align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                Text("Rest State", color = Color.White)
                FloatingPillNavBar(items = items, backdrop = backdrop)
            }
        }
    }
}

@Composable
private fun NavBarIcon(
    item: NavBarItem,
    onPressedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) { onPressedChanged(isPressed) }

    val contentColor by
        animateColorAsState(
            targetValue =
                if (item.isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "navContentColor",
        )
    val currentIcon = if (item.isSelected) item.selectedIcon else item.unselectedIcon

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(CircleShape)
                .bubbleFluidity(targetScale = 1.15f, pullFactor = 0.03f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = item.onClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = currentIcon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = item.contentDescription,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
