package com.dexstudios.dex.core.network

data class DownloadState(
    val fileName: String = "",
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val doneFiles: Int = 0,
    val totalFiles: Int = 1,
    val protocol: String = "",
    val speedBps: Long = 0L,
    val sourceFingerprint: String? = null,
    val peerName: String? = null,
    val peerPicture: String? = null
)
