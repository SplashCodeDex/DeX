package com.dexstudios.dex.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.zip.ZipInputStream

object AdbManager {
    private val dexSettingsDir = File(System.getProperty("user.home"), ".dex_settings")
    private val toolsDir = File(dexSettingsDir, "tools")
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    private val adbExeName = if (isWindows) "adb.exe" else "adb"
    private val adbExeFile = File(toolsDir, "platform-tools/$adbExeName")

    private val downloadOsSuffix = if (isWindows) "windows" else "darwin"
    private val ADB_DOWNLOAD_URL = "https://dl.google.com/android/repository/platform-tools-latest-$downloadOsSuffix.zip"

    /**
     * Tries to find ADB in the system PATH. If not found, uses the bundled version.
     * If bundled version doesn't exist, downloads and extracts it.
     */
    suspend fun getAdbExecutable(): String = withContext(Dispatchers.IO) {
        // Check if adb is in PATH
        try {
            val process = ProcessBuilder("adb", "version").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                return@withContext "adb"
            }
        } catch (e: Exception) {
            // adb not in PATH, proceed to fallback
        }

        // Check if we have it downloaded
        if (adbExeFile.exists()) {
            return@withContext adbExeFile.absolutePath
        }

        // Download and extract
        println("ADB not found in PATH. Downloading platform-tools...")
        try {
            if (!toolsDir.exists()) {
                toolsDir.mkdirs()
            }

            val zipFile = File(toolsDir, "platform-tools.zip")
            URI(ADB_DOWNLOAD_URL).toURL().openStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    input.copyTo(output)
                }
            }

            println("Extracting platform-tools...")
            ZipInputStream(zipFile.inputStream()).use { zis ->
                val canonicalToolsDir = toolsDir.canonicalPath
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(toolsDir, entry.name)
                    val canonicalDestination = newFile.canonicalPath
                    if (!canonicalDestination.startsWith(canonicalToolsDir)) {
                        throw SecurityException("Zip entry is outside of the target directory: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        if (!isWindows && entry.name.endsWith("adb")) {
                            newFile.setExecutable(true)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Cleanup zip
            zipFile.delete()
            println("ADB downloaded and extracted successfully to ${adbExeFile.absolutePath}")

        } catch (e: Exception) {
            println("Failed to download or extract platform-tools: ${e.message}")
            e.printStackTrace()
            // Fallback to naive 'adb' hoping it somehow works
            return@withContext "adb"
        }

        return@withContext if (adbExeFile.exists()) adbExeFile.absolutePath else "adb"
    }

    /**
     * Pre-checks port 5555 using a 400ms TCP ping to avoid indefinite adb hang on offline devices.
     */
    private suspend fun isAdbPortOpen(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, 5555), 400)
                return@withContext true
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun connect(ip: String) = withContext(Dispatchers.IO) {
        val targetIp = ip.ifBlank { "127.0.0.1" }
        println("Attempting ADB connect to $targetIp...")
        
        if (!isAdbPortOpen(targetIp)) {
            println("ADB connect failed: Port 5555 is not open or unreachable within 400ms on $targetIp")
            return@withContext
        }

        val adbCmd = getAdbExecutable()
        try {
            val process = ProcessBuilder(adbCmd, "connect", "$targetIp:5555").start()
            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()
            println("ADB Connect Result: $result")
        } catch (e: Exception) {
            println("Error connecting ADB: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun disconnect(ip: String) = withContext(Dispatchers.IO) {
        val targetIp = ip.ifBlank { "127.0.0.1" }
        println("Attempting ADB disconnect from $targetIp...")
        val adbCmd = getAdbExecutable()
        try {
            val process = ProcessBuilder(adbCmd, "disconnect", "$targetIp:5555").start()
            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()
            println("ADB Disconnect Result: $result")
        } catch (e: Exception) {
            println("Error disconnecting ADB: ${e.message}")
            e.printStackTrace()
        }
    }
}
