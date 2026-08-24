package com.dexstudios.dex.window.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.TransferHistory
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import com.dexstudios.dex.core.network.services.ExplorerFileEntry
import com.dexstudios.dex.core.network.services.ExplorerFolderItem
import com.dexstudios.dex.core.network.services.FileExplorerService
import com.dexstudios.dex.core.network.services.PullFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class FileExplorerViewModel(private val clientEngine: ClientEngine, private val fileExplorerService: FileExplorerService, private val discoveryEngine: DiscoveryEngine) : ViewModel() {

    private val rootDirectory: String = getDeXDownloadDirectory()

    private val _mode = MutableStateFlow(ExplorerMode.History)
    val mode = _mode.asStateFlow()

    private val _currentLocalPath = MutableStateFlow(rootDirectory)
    val currentLocalPath = _currentLocalPath.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class)
    val debouncedQuery = _searchQuery.debounce(150).stateIn(viewModelScope, SharingStarted.Lazily, "")

    private val _selectedItemId = MutableStateFlow<String?>(null)
    val selectedItemId = _selectedItemId.asStateFlow()

    private val _safFolders = MutableStateFlow<List<ExplorerFolderItem>>(emptyList())
    val safFolders = _safFolders.asStateFlow()

    private val _safBreadcrumb = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val safBreadcrumb = _safBreadcrumb.asStateFlow()

    private val _safEntries = MutableStateFlow<List<ExplorerFileEntry>>(emptyList())
    val safEntries = _safEntries.asStateFlow()

    private val _isLoadingSaf = MutableStateFlow(false)
    val isLoadingSaf = _isLoadingSaf.asStateFlow()

    private val _explorerError = MutableStateFlow<String?>(null)
    val explorerError = _explorerError.asStateFlow()

    fun clearError() {
        _explorerError.value = null
    }

    val pairedFingerprints = AuthState.pairedFingerprints
    val devicesMap = discoveryEngine.devices

    val activePhone = combine(devicesMap, pairedFingerprints) { map, paired ->
        map.values.firstOrNull { paired.contains(it.info.fingerprint) } ?: map.values.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val activeFingerprint = combine(devicesMap, pairedFingerprints) { map, paired ->
        val connected = WebSocketConnectionManager.connectedFingerprints()
        paired.firstOrNull { it in connected }
            ?: map.values.firstOrNull { !it.viaWan && it.info.fingerprint in paired }?.info?.fingerprint
            ?: map.values.firstOrNull()?.info?.fingerprint
            ?: paired.firstOrNull()
            ?: ""
    }.stateIn(viewModelScope, SharingStarted.Lazily, "")

    val transferHistoryItems = TransferHistory.items

    val isTransferring = combine(clientEngine.uploadState, fileExplorerService.pullProgress) { upload, pull ->
        upload.isUploading || pull.isPulling
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Lazily, false)

    init {
        TransferHistory.init()

        // Load SAF root folders when switching to SAF mode
        viewModelScope.launch {
            combine(_mode, activeFingerprint) { m, fp -> Pair(m, fp) }.collectLatest { (m, fp) ->
                if (m == ExplorerMode.Saf && fp.isNotBlank()) {
                    _isLoadingSaf.value = true
                    try {
                        _safBreadcrumb.value = emptyList()
                        if (!WebSocketConnectionManager.isConnected(fp)) {
                            _safFolders.value = emptyList()
                            _explorerError.value = "No phone connected"
                        } else {
                            _safFolders.value = fileExplorerService.listFolders(fp)
                        }
                    } finally {
                        _isLoadingSaf.value = false
                    }
                }
            }
        }

        // Load SAF directory entries when breadcrumb changes
        viewModelScope.launch {
            combine(_safBreadcrumb, activeFingerprint) { breadcrumb, fp -> Pair(breadcrumb, fp) }.collectLatest { (breadcrumb, fp) ->
                if (_mode.value == ExplorerMode.Saf && breadcrumb.isNotEmpty() && fp.isNotBlank()) {
                    _isLoadingSaf.value = true
                    try {
                        if (!WebSocketConnectionManager.isConnected(fp)) {
                            _safEntries.value = emptyList()
                            _explorerError.value = "No phone connected"
                        } else {
                            val currentFolderUri = breadcrumb.last().second
                            _safEntries.value = fileExplorerService.browseFolder(fp, currentFolderUri)
                        }
                    } finally {
                        _isLoadingSaf.value = false
                    }
                }
            }
        }
    }

    // Inner combine groups the SAF-related flows into a single tuple (max 5-param overload).
    private data class SafSnapshot(val folders: List<ExplorerFolderItem>, val breadcrumb: List<Pair<String, String>>, val entries: List<ExplorerFileEntry>)

    private val safSnapshot: Flow<SafSnapshot> = combine(
        _safFolders,
        _safBreadcrumb,
        _safEntries,
    ) { folders, breadcrumb, entries ->
        SafSnapshot(folders, breadcrumb, entries)
    }

    val displayedFiles = combine(
        _mode,
        _currentLocalPath,
        transferHistoryItems,
        safSnapshot,
        debouncedQuery,
    ) { m, path, history, saf, query ->
        val rawItems = if (m == ExplorerMode.History) {
            val folder = File(path)
            val diskFiles = if (folder.exists() && folder.isDirectory) {
                folder.listFiles()?.map { f ->
                    ExplorerFileItem(
                        id = f.absolutePath,
                        name = f.name,
                        path = f.absolutePath,
                        size = if (f.isDirectory) 0L else f.length(),
                        isDirectory = f.isDirectory,
                        timestamp = f.lastModified(),
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }

            if (diskFiles.isEmpty() && history.isNotEmpty()) {
                history.map { record ->
                    ExplorerFileItem(
                        id = record.id,
                        name = record.name,
                        path = record.uri ?: "",
                        size = record.size,
                        isDirectory = false,
                        timestamp = record.timestamp,
                        uri = record.uri,
                    )
                }
            } else {
                diskFiles
            }
        } else {
            if (saf.breadcrumb.isEmpty()) {
                saf.folders.map { f ->
                    ExplorerFileItem(
                        id = f.id,
                        name = f.name,
                        path = f.uri,
                        size = 0L,
                        isDirectory = true,
                        timestamp = System.currentTimeMillis(),
                        uri = f.uri,
                    )
                } + ExplorerFileItem(
                    id = "add_saf_folder",
                    name = "+ Add Folder",
                    path = "",
                    size = 0L,
                    isDirectory = true,
                    timestamp = 0L,
                    isAddFolderButton = true,
                )
            } else {
                saf.entries.map { e ->
                    ExplorerFileItem(
                        id = e.uri,
                        name = e.name,
                        path = e.uri,
                        size = e.size,
                        isDirectory = e.isDirectory,
                        timestamp = System.currentTimeMillis(),
                        uri = e.uri,
                        thumbBase64 = e.thumbBase64,
                    )
                }
            }
        }

        if (query.isBlank()) rawItems else rawItems.filter { it.name.contains(query, ignoreCase = true) }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isAtRoot = combine(_mode, _currentLocalPath, _safBreadcrumb) { m, path, breadcrumb ->
        if (m == ExplorerMode.History) {
            path == rootDirectory || File(path).parent == null
        } else {
            breadcrumb.isEmpty()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun toggleMode() {
        _selectedItemId.value = null
        _mode.value = if (_mode.value == ExplorerMode.History) ExplorerMode.Saf else ExplorerMode.History
    }

    fun navigateUp() {
        if (_mode.value == ExplorerMode.History) {
            val parent = File(_currentLocalPath.value).parent
            if (parent != null) {
                _currentLocalPath.value = parent
            }
        } else {
            if (_safBreadcrumb.value.isNotEmpty()) {
                _safBreadcrumb.value = _safBreadcrumb.value.dropLast(1)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectItem(id: String) {
        _selectedItemId.value = id
    }

    fun grantNewFolder() {
        viewModelScope.launch {
            val fp = activeFingerprint.value
            if (fp.isBlank() || !WebSocketConnectionManager.isConnected(fp)) {
                _explorerError.value = "No phone connected"
                return@launch
            }
            _isLoadingSaf.value = true
            try {
                val newFolder = fileExplorerService.grantFolder(fp)
                if (newFolder != null) {
                    _safFolders.value = fileExplorerService.listFolders(fp)
                }
            } finally {
                _isLoadingSaf.value = false
            }
        }
    }

    fun drillDown(path: String, name: String, uri: String?) {
        if (_mode.value == ExplorerMode.History) {
            _currentLocalPath.value = path
        } else {
            _safBreadcrumb.value = _safBreadcrumb.value + Pair(name, uri ?: path)
        }
    }

    fun pullSafFile(uri: String, name: String, size: Long) {
        viewModelScope.launch {
            val fp = activeFingerprint.value
            if (fp.isBlank() || !WebSocketConnectionManager.isConnected(fp)) {
                _explorerError.value = "No phone connected"
                return@launch
            }
            fileExplorerService.pullFiles(fp, listOf(PullFileItem(uri = uri, name = name, size = size)))
        }
    }

    fun cancelPull() {
        clientEngine.resetUploadState()
        val currentPull = fileExplorerService.pullProgress.value
        val fp = activeFingerprint.value
        if (currentPull.isPulling && fp.isNotBlank()) {
            viewModelScope.launch {
                fileExplorerService.cancelPull(fp, currentPull.requestId)
            }
        }
    }
}
