package com.dexstudios.dex.core.designsystem.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object TopAppBarState {
    var isProfileExpanded by mutableStateOf(false)
    var isSearchExpanded by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var isOnboardingVisible by mutableStateOf(false)

    // History Filters
    var historyDirectionFilter by mutableStateOf(HistoryDirection.ALL)
    var historyTypeFilter by mutableStateOf(HistoryType.ALL)
    var historySortOrder by mutableStateOf(HistorySort.DATE_DESC)
    var historyViewMode by mutableStateOf(HistoryViewMode.LIST)
    var isHistoryFilterVisible by mutableStateOf(false)
}

enum class HistoryDirection { ALL, SENT, RECEIVED }
enum class HistoryType { ALL, IMAGES, VIDEOS, DOCUMENTS, APPS }
enum class HistorySort { DATE_DESC, SIZE_DESC, NAME_ASC }
enum class HistoryViewMode { LIST, GRID }
