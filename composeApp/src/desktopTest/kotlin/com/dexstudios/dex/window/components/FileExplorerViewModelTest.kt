package com.dexstudios.dex.window.components

import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.services.ExplorerFileEntry
import com.dexstudios.dex.core.network.services.ExplorerFolderItem
import com.dexstudios.dex.core.network.services.FileExplorerService
import com.dexstudios.dex.core.network.services.PullProgressState
import com.dexstudios.dex.core.network.TransferHistory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class FileExplorerViewModelTest {

    private lateinit var clientEngine: ClientEngine
    private lateinit var fileExplorerService: FileExplorerService
    private lateinit var discoveryEngine: DiscoveryEngine
    
    private lateinit var viewModel: FileExplorerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        mockkObject(TransferHistory)
        every { TransferHistory.init() } returns Unit
        every { TransferHistory.items } returns MutableStateFlow(emptyList())

        clientEngine = mockk(relaxed = true)
        fileExplorerService = mockk(relaxed = true)
        discoveryEngine = mockk(relaxed = true)

        // Setup flows for dependencies
        every { discoveryEngine.devices } returns MutableStateFlow(emptyMap())
        every { clientEngine.uploadState } returns MutableStateFlow(com.dexstudios.dex.core.network.UploadState())
        every { fileExplorerService.pullProgress } returns MutableStateFlow(PullProgressState())

        viewModel = FileExplorerViewModel(
            clientEngine = clientEngine,
            fileExplorerService = fileExplorerService,
            discoveryEngine = discoveryEngine
        )
    }

    @After
    fun tearDown() {
        unmockkObject(TransferHistory)
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial state is History mode`() = runTest {
        assertEquals(ExplorerMode.History, viewModel.mode.value)
    }

    @Test
    fun `test toggleMode switches between History and Saf`() = runTest {
        assertEquals(ExplorerMode.History, viewModel.mode.value)
        
        viewModel.toggleMode()
        assertEquals(ExplorerMode.Saf, viewModel.mode.value)
        
        viewModel.toggleMode()
        assertEquals(ExplorerMode.History, viewModel.mode.value)
    }

    @Test
    fun `test drillDown in SAF mode adds to breadcrumb`() = runTest {
        viewModel.toggleMode() // Switch to SAF
        assertEquals(0, viewModel.safBreadcrumb.value.size)

        viewModel.drillDown("path/to/folder", "My Folder", "content://uri/123")
        
        val breadcrumb = viewModel.safBreadcrumb.value
        assertEquals(1, breadcrumb.size)
        assertEquals("My Folder", breadcrumb.first().first)
        assertEquals("content://uri/123", breadcrumb.first().second)
    }

    @Test
    fun `test navigateUp in SAF mode pops breadcrumb`() = runTest {
        viewModel.toggleMode()
        viewModel.drillDown("path1", "Folder1", "uri1")
        viewModel.drillDown("path2", "Folder2", "uri2")
        
        assertEquals(2, viewModel.safBreadcrumb.value.size)
        
        viewModel.navigateUp()
        assertEquals(1, viewModel.safBreadcrumb.value.size)
        assertEquals("Folder1", viewModel.safBreadcrumb.value.first().first)
        
        viewModel.navigateUp()
        assertEquals(0, viewModel.safBreadcrumb.value.size)
        
        // Ensure it doesn't crash on empty
        viewModel.navigateUp()
        assertEquals(0, viewModel.safBreadcrumb.value.size)
    }
}
