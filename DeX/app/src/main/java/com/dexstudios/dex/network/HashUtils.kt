package com.dexstudios.dex.network
import timber.log.Timber

import android.content.Context
import android.net.Uri
import java.security.MessageDigest
import kotlin.math.min

object HashUtils {
    private const val PARTIAL_SIZE = 32768 // 32KB

    fun computePartialHash(context: Context, uri: Uri, fileSize: Long): String? {
        if (fileSize <= 0L) return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                computePartialHash(stream, fileSize)
            }
        } catch (e: Exception) {
            Timber.e(e, "Operation failed")
            null
        }
    }

    private fun readFully(stream: java.io.InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var totalRead = 0
        while (totalRead < length) {
            val count = stream.read(buffer, offset + totalRead, length - totalRead)
            if (count == -1) break
            totalRead += count
        }
        return totalRead
    }

    private fun skipFully(stream: java.io.InputStream, bytesToSkip: Long): Long {
        var totalSkipped = 0L
        val discard = ByteArray(8192)
        while (totalSkipped < bytesToSkip) {
            val remaining = bytesToSkip - totalSkipped
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                totalSkipped += skipped
            } else {
                val toRead = min(remaining, discard.size.toLong()).toInt()
                val read = stream.read(discard, 0, toRead)
                if (read <= 0) break
                totalSkipped += read
            }
        }
        return totalSkipped
    }

    fun computePartialHash(stream: java.io.InputStream, fileSize: Long): String? {
        if (fileSize <= 0L) return null
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(PARTIAL_SIZE)

            // Read head fully (up to 32KB)
            val headToRead = min(fileSize, PARTIAL_SIZE.toLong()).toInt()
            val bytesReadHead = readFully(stream, buffer, 0, headToRead)
            if (bytesReadHead > 0) {
                md.update(buffer, 0, bytesReadHead)
            }

            // If file is large enough, skip to the last 32KB and hash it
            if (fileSize > PARTIAL_SIZE * 2) {
                val bytesToSkip = fileSize - bytesReadHead - PARTIAL_SIZE
                val skipped = skipFully(stream, bytesToSkip)
                if (skipped == bytesToSkip) {
                    val bytesReadTail = readFully(stream, buffer, 0, PARTIAL_SIZE)
                    if (bytesReadTail > 0) {
                        md.update(buffer, 0, bytesReadTail)
                    }
                }
            } else if (fileSize > bytesReadHead) {
                // File is between 32KB and 64KB, just read the rest
                var remaining = fileSize - bytesReadHead
                while (remaining > 0) {
                    val toRead = min(remaining, buffer.size.toLong()).toInt()
                    val read = readFully(stream, buffer, 0, toRead)
                    if (read <= 0) break
                    md.update(buffer, 0, read)
                    remaining -= read
                }
            }

            md.digest().joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Operation failed")
            null
        }
    }

    fun hmacSha256Base64(secret: String, data: ByteArray): String =
        com.dexstudios.dex.core.network.HashUtils.hmacSha256Base64(secret, data)
}
