package com.dexstudios.dex.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class HistoryDirection { ALL, SENT, RECEIVED }
enum class HistoryType { ALL, IMAGES, VIDEOS, DOCUMENTS, APPS }
enum class HistorySort { DATE_DESC, SIZE_DESC, NAME_ASC }
enum class HistoryViewMode { LIST, GRID }

/**
 * Dedicated state holder for transfer history filtering and the Sheet Search Island (plan 044).
 */
object HistoryState {
    var searchQuery by mutableStateOf("")
    var isSearchExpanded by mutableStateOf(false)

    var directionFilter by mutableStateOf(HistoryDirection.ALL)
    var typeFilter by mutableStateOf(HistoryType.ALL)
    var sortOrder by mutableStateOf(HistorySort.DATE_DESC)
    var viewMode by mutableStateOf(HistoryViewMode.LIST)
    var isFilterVisible by mutableStateOf(false)

    fun clearSearch() {
        searchQuery = ""
        isSearchExpanded = false
    }

    fun toggleFilterVisibility() {
        isFilterVisible = !isFilterVisible
    }

    fun toggleViewMode() {
        viewMode = if (viewMode == HistoryViewMode.LIST) HistoryViewMode.GRID else HistoryViewMode.LIST
    }

    fun resetFilters() {
        directionFilter = HistoryDirection.ALL
        typeFilter = HistoryType.ALL
        sortOrder = HistorySort.DATE_DESC
    }
}
