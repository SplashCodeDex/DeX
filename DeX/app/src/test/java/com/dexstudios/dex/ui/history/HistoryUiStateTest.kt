package com.dexstudios.dex.ui.history

import com.dexstudios.dex.ui.state.TopAppBarState
import com.dexstudios.dex.ui.state.TopIslandState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HistoryUiStateTest {

    @Before
    fun setUp() {
        HistoryState.clearSearch()
        HistoryState.resetFilters()
        HistoryState.viewMode = HistoryViewMode.LIST
        HistoryState.isFilterVisible = false
        TopIslandState.isProfileExpanded = false
        TopIslandState.isOnboardingVisible = false
    }

    @Test
    fun historyState_initialValues_areExpected() {
        assertEquals("", HistoryState.searchQuery)
        assertFalse(HistoryState.isSearchExpanded)
        assertEquals(HistoryDirection.ALL, HistoryState.directionFilter)
        assertEquals(HistoryType.ALL, HistoryState.typeFilter)
        assertEquals(HistorySort.DATE_DESC, HistoryState.sortOrder)
        assertEquals(HistoryViewMode.LIST, HistoryState.viewMode)
        assertFalse(HistoryState.isFilterVisible)
    }

    @Test
    fun clearSearch_resetsQueryAndCollapses() {
        HistoryState.searchQuery = "vacation_photos.zip"
        HistoryState.isSearchExpanded = true

        HistoryState.clearSearch()

        assertEquals("", HistoryState.searchQuery)
        assertFalse(HistoryState.isSearchExpanded)
    }

    @Test
    fun toggleFilterVisibility_flipsBoolean() {
        assertFalse(HistoryState.isFilterVisible)
        HistoryState.toggleFilterVisibility()
        assertTrue(HistoryState.isFilterVisible)
        HistoryState.toggleFilterVisibility()
        assertFalse(HistoryState.isFilterVisible)
    }

    @Test
    fun toggleViewMode_flipsBetweenListAndGrid() {
        assertEquals(HistoryViewMode.LIST, HistoryState.viewMode)
        HistoryState.toggleViewMode()
        assertEquals(HistoryViewMode.GRID, HistoryState.viewMode)
        HistoryState.toggleViewMode()
        assertEquals(HistoryViewMode.LIST, HistoryState.viewMode)
    }

    @Test
    fun resetFilters_restoresDefaults() {
        HistoryState.directionFilter = HistoryDirection.SENT
        HistoryState.typeFilter = HistoryType.VIDEOS
        HistoryState.sortOrder = HistorySort.SIZE_DESC

        HistoryState.resetFilters()

        assertEquals(HistoryDirection.ALL, HistoryState.directionFilter)
        assertEquals(HistoryType.ALL, HistoryState.typeFilter)
        assertEquals(HistorySort.DATE_DESC, HistoryState.sortOrder)
    }

    @Test
    fun topIslandState_mutations_workDirectly() {
        assertFalse(TopIslandState.isProfileExpanded)
        TopIslandState.isProfileExpanded = true
        assertTrue(TopIslandState.isProfileExpanded)

        assertFalse(TopIslandState.isOnboardingVisible)
        TopIslandState.isOnboardingVisible = true
        assertTrue(TopIslandState.isOnboardingVisible)
    }

    @Test
    fun topAppBarState_facade_forwardsCorrectly() {
        TopAppBarState.isProfileExpanded = true
        assertTrue(TopIslandState.isProfileExpanded)

        TopAppBarState.searchQuery = "test query"
        assertEquals("test query", HistoryState.searchQuery)

        TopAppBarState.isSearchExpanded = true
        assertTrue(HistoryState.isSearchExpanded)

        TopAppBarState.historyDirectionFilter = HistoryDirection.RECEIVED
        assertEquals(HistoryDirection.RECEIVED, HistoryState.directionFilter)

        TopAppBarState.historyTypeFilter = HistoryType.DOCUMENTS
        assertEquals(HistoryType.DOCUMENTS, HistoryState.typeFilter)

        TopAppBarState.historySortOrder = HistorySort.NAME_ASC
        assertEquals(HistorySort.NAME_ASC, HistoryState.sortOrder)
    }
}
