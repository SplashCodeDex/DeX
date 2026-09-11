package com.dexstudios.dex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.island.DynamicDimensions
import com.dexstudios.dex.ui.components.island.DynamicPillButton
import com.dexstudios.dex.ui.history.HistoryState
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * High-performance liquid glass bouncy expanding Search Island for the sheet header (plan 044).
 *
 * Sits anchored at the top-right corner of the sheet card when History mode is active.
 * Retains 100% of CodeDeX's tuned signature spring kinematics, tactile bubble fluidity,
 * multi-tier liquid-glass shadows, and transient optical content blur via [DynamicPillButton].
 */
@Composable
fun SheetSearchIsland(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val isSearchExpanded = HistoryState.isSearchExpanded
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val expandedWidth = (screenWidth - 32.dp).coerceAtLeast(160.dp)

    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            delay(150.milliseconds) // Wait for spring animation to initiate
            searchFocusRequester.requestFocus()
        } else {
            keyboardController?.hide()
            HistoryState.searchQuery = "" // Clear search when collapsed
        }
    }

    val contentColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .zIndex(if (isSearchExpanded) 20f else 5f),
        contentAlignment = Alignment.TopEnd
    ) {
        DynamicPillButton(
            isExpanded = isSearchExpanded,
            onExpandedChange = { HistoryState.isSearchExpanded = it },
            dimensions = DynamicDimensions(
                collapsedWidth = 56.dp,
                collapsedHeight = 56.dp,
                expandedWidth = expandedWidth,
                expandedHeight = 72.dp,
            ),
            collapsedGlassConfig = LiquidGlassPresets.SearchIconButton,
            expandedGlassConfig = LiquidGlassPresets.SearchIsland,
            backdrop = backdrop,
            collapsedContent = {
                Icon(
                    imageVector = MaterialSymbols.Search,
                    contentDescription = "Search history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            },
            expandedContent = { collapse ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(onClick = collapse),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Search,
                            contentDescription = "Collapse search",
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = HistoryState.searchQuery,
                        onValueChange = { HistoryState.searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = contentColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(contentColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                        decorationBox = { innerTextField ->
                            if (HistoryState.searchQuery.isEmpty()) {
                                Text(
                                    "Search history...",
                                    color = contentColor.copy(alpha = 0.35f),
                                    fontSize = 18.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (HistoryState.searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DynamicDismissButton(
                                onClick = { HistoryState.searchQuery = "" },
                                size = DynamicDismissButtonSize.Medium,
                                colors = DynamicDismissButtonDefaults.colors(
                                    containerColor = contentColor.copy(alpha = 0.08f),
                                    contentColor = contentColor.copy(alpha = 0.60f)
                                ),
                                contentDescription = "Clear search"
                            )
                        }
                    }
                }
            }
        )
    }
}
