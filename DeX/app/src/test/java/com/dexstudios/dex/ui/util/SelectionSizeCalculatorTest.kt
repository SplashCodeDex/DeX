package com.dexstudios.dex.ui.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectionSizeCalculatorTest {
    @Test
    fun calculationUsesSnapshotsAndDoesNotWriteCallerCache() = runTest {
        val gate = CompletableDeferred<Unit>()
        val selection = mutableListOf("a", "b")
        val cache = mutableMapOf("b" to 20L)
        val calculator = SelectionSizeCalculator<String>({ gate.await(); 10L }, StandardTestDispatcher(testScheduler))
        val pending = async { calculator.calculate(selection, cache) }
        runCurrent()
        selection.clear()
        cache["b"] = 999L
        gate.complete(Unit)
        val result = pending.await()
        assertEquals(30L, result.totalBytes)
        assertEquals(mapOf("a" to 10L, "b" to 20L), result.sizes)
        assertEquals(mapOf("b" to 999L), cache)
    }

    @Test
    fun cancelledProviderResultCannotPublishAfterNewSelection() = runTest {
        val gate = CompletableDeferred<Unit>()
        val calculator = SelectionSizeCalculator<String>({ item ->
            if (item == "old") withContext(NonCancellable) { gate.await() }
            if (item == "old") 100L else 7L
        }, StandardTestDispatcher(testScheduler))
        var published = 0L
        val old = launch { published = calculator.calculate(listOf("old"), emptyMap()).totalBytes }
        runCurrent()
        old.cancel()
        published = calculator.calculate(listOf("new"), emptyMap()).totalBytes
        gate.complete(Unit)
        old.join()
        assertTrue(old.isCancelled)
        assertEquals(7L, published)
    }
}
