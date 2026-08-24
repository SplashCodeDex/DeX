package com.dexstudios.dex.core.network.server

import java.io.File

object ReceiveStorage {
    private const val DOWNLOAD_DIR_NAME = "Downloads/DeX"

    fun downloadsDir(): File = File(System.getProperty("user.home"), DOWNLOAD_DIR_NAME).apply { mkdirs() }
}
