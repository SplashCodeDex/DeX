package com.dexstudios.dex.core.designsystem.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun DeXScrollbar(listState: LazyListState, modifier: Modifier = Modifier)
