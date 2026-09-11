package com.dexstudios.dex.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dexstudios.dex.ui.history.HistoryDirection
import com.dexstudios.dex.ui.history.HistorySort
import com.dexstudios.dex.ui.history.HistoryState
import com.dexstudios.dex.ui.history.HistoryType
import com.dexstudios.dex.ui.history.HistoryViewMode

enum class ProfileExpansionStage { Collapsed, NamePill, FullIsland }

/**
 * State holder for the top Dynamic Island (plan 044).
 */
object TopIslandState {
    var profileStage by mutableStateOf(ProfileExpansionStage.Collapsed)
    var isOnboardingVisible by mutableStateOf(false)

    val isAnyProfileExpanded: Boolean
        get() = profileStage != ProfileExpansionStage.Collapsed

    var isProfileExpanded: Boolean
        get() = profileStage == ProfileExpansionStage.FullIsland
        set(value) {
            profileStage = if (value) ProfileExpansionStage.FullIsland else ProfileExpansionStage.Collapsed
        }

    fun advanceProfileStage() {
        profileStage = when (profileStage) {
            ProfileExpansionStage.Collapsed -> ProfileExpansionStage.NamePill
            ProfileExpansionStage.NamePill -> ProfileExpansionStage.FullIsland
            ProfileExpansionStage.FullIsland -> ProfileExpansionStage.Collapsed
        }
    }

    fun collapseProfile() {
        profileStage = ProfileExpansionStage.Collapsed
    }
}

/**
 * Backward-compatibility façade for TopAppBarState (delegates to TopIslandState and HistoryState).
 */
object TopAppBarState {
    var isProfileExpanded: Boolean
        get() = TopIslandState.isProfileExpanded
        set(value) { TopIslandState.isProfileExpanded = value }

    var isOnboardingVisible: Boolean
        get() = TopIslandState.isOnboardingVisible
        set(value) { TopIslandState.isOnboardingVisible = value }

    var isSearchExpanded: Boolean
        get() = HistoryState.isSearchExpanded
        set(value) { HistoryState.isSearchExpanded = value }

    var searchQuery: String
        get() = HistoryState.searchQuery
        set(value) { HistoryState.searchQuery = value }

    // History Filters (forwarded to HistoryState)
    var historyDirectionFilter: HistoryDirection
        get() = HistoryState.directionFilter
        set(value) { HistoryState.directionFilter = value }

    var historyTypeFilter: HistoryType
        get() = HistoryState.typeFilter
        set(value) { HistoryState.typeFilter = value }

    var historySortOrder: HistorySort
        get() = HistoryState.sortOrder
        set(value) { HistoryState.sortOrder = value }

    var historyViewMode: HistoryViewMode
        get() = HistoryState.viewMode
        set(value) { HistoryState.viewMode = value }

    var isHistoryFilterVisible: Boolean
        get() = HistoryState.isFilterVisible
        set(value) { HistoryState.isFilterVisible = value }
}

// Typealiases for enums so external references resolve seamlessly
typealias HistoryDirection = com.dexstudios.dex.ui.history.HistoryDirection
typealias HistoryType = com.dexstudios.dex.ui.history.HistoryType
typealias HistorySort = com.dexstudios.dex.ui.history.HistorySort
typealias HistoryViewMode = com.dexstudios.dex.ui.history.HistoryViewMode
