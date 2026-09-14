package com.dexstudios.dex.ui.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Returns new values from snapshots; never mutates live UI collections. */
class SelectionSizeCalculator<K>(
    private val resolveSize: suspend (K) -> Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    data class Result<K>(val sizes: Map<K, Long>, val totalBytes: Long)

    suspend fun calculate(selection: List<K>, cachedSizes: Map<K, Long>): Result<K> {
        val snapshot = selection.toList()
        val cacheSnapshot = cachedSizes.toMap()
        return withContext(ioDispatcher) {
            val resolved = mutableMapOf<K, Long>()
            var total = 0L
            for (item in snapshot) {
                currentCoroutineContext().ensureActive()
                val size = resolved[item] ?: cacheSnapshot[item] ?: resolveSize(item).coerceAtLeast(0L)
                currentCoroutineContext().ensureActive()
                resolved[item] = size
                total = if (size > Long.MAX_VALUE - total) Long.MAX_VALUE else total + size
            }
            Result(resolved, total)
        }
    }
}
