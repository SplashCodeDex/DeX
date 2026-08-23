package com.dexstudios.dex.window.components

enum class ExplorerMode {
    History,
    Saf
}

data class ExplorerFileItem(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val timestamp: Long,
    val uri: String? = null,
    val thumbBase64: String? = null,
    val isAddFolderButton: Boolean = false
)
