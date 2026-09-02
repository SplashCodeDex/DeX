package com.dexstudios.dex.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.R
import com.dexstudios.dex.ui.components.glass.LiquidGlassIconButton
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.state.TopAppBarState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private var isProfileExpanded: Boolean
    get() = TopAppBarState.isProfileExpanded
    set(value) { TopAppBarState.isProfileExpanded = value }

private var isSearchExpanded: Boolean
    get() = TopAppBarState.isSearchExpanded
    set(value) { TopAppBarState.isSearchExpanded = value }

@Composable
fun FloatingTopAppBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    showSearch: Boolean = true,
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val expandedWidth = screenWidth - 32.dp

    // Dynamic Island bouncy expansion (Search)
    val searchWidth by animateDpAsState(
        targetValue = if (isSearchExpanded) expandedWidth else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "searchWidth"
    )
    val searchHeight by animateDpAsState(
        targetValue = if (isSearchExpanded) 72.dp else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "searchHeight"
    )

    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            delay(100.milliseconds) // Wait for animation to start
            searchFocusRequester.requestFocus()
        } else {
            keyboardController?.hide()
            TopAppBarState.searchQuery = "" // Clear search when collapsed
        }
    }

    val searchAlpha by animateFloatAsState(
        targetValue = if (isProfileExpanded) 0f else 1f,
        animationSpec = tween(400),
        label = "searchAlpha"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        // The Top Bar Layout (logo space reserved)
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .height(80.dp)
        )

        // Action Buttons Group / Search Island (Right side)
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 16.dp)
                .zIndex(if (isSearchExpanded) 2f else 1f)
                .graphicsLayer { alpha = searchAlpha },
            contentAlignment = Alignment.TopEnd
        ) {
            if (showSearch) {
                LiquidGlassIconButton(
                    onClick = {
                        isSearchExpanded = !isSearchExpanded
                        if (isSearchExpanded) isProfileExpanded = false
                    },
                    width = searchWidth,
                    height = searchHeight,
                    backdrop = backdrop,
                    config = if (isSearchExpanded) LiquidGlassPresets.SearchIsland else LiquidGlassPresets.SearchIconButton
                ) {
                    AnimatedContent(
                        targetState = isSearchExpanded,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        },
                        label = "searchContent"
                    ) { expanded ->
                        if (expanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Search,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = TopAppBarState.searchQuery,
                                    onValueChange = { TopAppBarState.searchQuery = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(searchFocusRequester),
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color.Black,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(Color.Black),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                                    decorationBox = { innerTextField ->
                                        if (TopAppBarState.searchQuery.isEmpty()) {
                                            Text(
                                                "Search devices...",
                                                color = Color.Black.copy(alpha = 0.35f),
                                                fontSize = 18.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (TopAppBarState.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { TopAppBarState.searchQuery = "" },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color.Black.copy(alpha = 0.08f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbols.Close,
                                            contentDescription = "Clear",
                                            tint = Color.Black.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Icon(
                                imageVector = MaterialSymbols.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(56.dp))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun TopAppBarSearchExpandedPreview() {
    TopAppBarState.isSearchExpanded = true
    TopAppBarState.isProfileExpanded = false
    MaterialTheme {
        val backdrop = rememberLayerBackdrop()
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.wallpaper_laptop),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            FloatingTopAppBar(backdrop = backdrop)
        }
    }
}
