package com.dexstudios.dex.desktop

import co.touchlab.kermit.Logger
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

    /** Resolved once per process; re-probing PATH and re-downloading on every call was pure churn. */
    @Volatile
    private var cachedAdbPath: String? = null

    /**
     * Tries to find ADB in the system PATH. If not found, uses the bundled version.
     * If bundled version doesn't exist, downloads and extracts it. The resolved path is
     * cached for the process lifetime — re-probing PATH and re-downloading on every call
     * was pure churn.
     */
    suspend fun getAdbExecutable(): String {
        cachedAdbPath?.let { return it }
        return resolveAdbExecutable().also { cachedAdbPath = it }
    }

    private suspend fun resolveAdbExecutable(): String = withContext(Dispatchers.IO) {
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
        Logger.i("ADB not found in PATH. Downloading platform-tools...")
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

            Logger.i("Extracting platform-tools...")
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
            Logger.i("ADB downloaded and extracted successfully to ${adbExeFile.absolutePath}")
        } catch (e: Exception) {
            Logger.i("Failed to download or extract platform-tools: ${e.message}")
            e.printStackTrace()
            // Fallback to naive 'adb' hoping it somehow works
            return@withContext "adb"
        }

        return@withContext if (adbExeFile.exists()) adbExeFile.absolutePath else "adb"
    }

    /**
     * Fires a broadcast at a connected device over ADB with the SAME executable resolution
     * as [connect]/[disconnect] (bundled platform-tools, not a bare PATH hope).
     * Bounded: a hung adb process is destroyed after [timeoutMillis] instead of leaking.
     * Returns true only when adb itself reported success — callers must treat this as
     * best-effort transport, never as delivery proof.
     */
    suspend fun broadcast(action: String, extras: Map<String, String>, timeoutMillis: Long = 5_000L): Boolean = withContext(Dispatchers.IO) {
        try {
            val args = buildList {
                add(getAdbExecutable())
                addAll(arrayOf("shell", "am", "broadcast", "-a", action))
                extras.forEach { (key, value) ->
                    addAll(arrayOf("-e", key, value))
                }
            }
            val process = ProcessBuilder(args).start()
            val finished = runCatching { process.waitFor(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS) }
                .getOrDefault(false)
            if (!finished) {
                process.destroyForcibly()
                Logger.i("ADB broadcast '$action' timed out after ${timeoutMillis}ms and was destroyed")
                return@withContext false
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                Logger.i("ADB broadcast '$action' failed (exit $exitCode)")
            }
            exitCode == 0
        } catch (e: Exception) {
            Logger.i("ADB broadcast '$action' error: ${e.message}")
            false
        }
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
        Logger.i("Attempting ADB connect to $targetIp...")

        if (!isAdbPortOpen(targetIp)) {
            Logger.i("ADB connect failed: Port 5555 is not open or unreachable within 400ms on $targetIp")
            return@withContext
        }

        val adbCmd = getAdbExecutable()
        try {
            val process = ProcessBuilder(adbCmd, "connect", "$targetIp:5555").start()
            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()
            Logger.i("ADB Connect Result: $result")
        } catch (e: Exception) {
            Logger.i("Error connecting ADB: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun disconnect(ip: String) = withContext(Dispatchers.IO) {
        val targetIp = ip.ifBlank { "127.0.0.1" }
        Logger.i("Attempting ADB disconnect from $targetIp...")
        val adbCmd = getAdbExecutable()
        try {
            val process = ProcessBuilder(adbCmd, "disconnect", "$targetIp:5555").start()
            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()
            Logger.i("ADB Disconnect Result: $result")
        } catch (e: Exception) {
            Logger.i("Error disconnecting ADB: ${e.message}")
            e.printStackTrace()
        }
    }
}
