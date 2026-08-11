package com.dexstudios.dex.ui.state

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object TopAppBarState {
    var isProfileExpanded by mutableStateOf(false)
    var isSearchExpanded by mutableStateOf(false)
}
