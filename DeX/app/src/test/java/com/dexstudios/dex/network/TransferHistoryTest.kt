package com.dexstudios.dex.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransferHistoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor

        // Start with clean state
        TransferHistory.clear(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `log should add record and persist it`() = runTest {
        val name = "test_file.txt"
        val size = 1024L
        val direction = "sent"

        TransferHistory.log(context, name, size, direction)

        val items = TransferHistory.items.value
        assertEquals(1, items.size)
        assertEquals(name, items[0].name)
        assertEquals(size, items[0].size)
        assertEquals(direction, items[0].direction)

        verify { editor.putString("transfers", any()) }
        verify { editor.apply() }
    }

    @Test
    fun `delete should remove specific record`() = runTest {
        TransferHistory.log(context, "file1", 100, "sent")
        TransferHistory.log(context, "file2", 200, "received")

        val idToDelete = TransferHistory.items.value[0].id
        TransferHistory.delete(context, idToDelete)

        val items = TransferHistory.items.value
        assertEquals(1, items.size)
        assertFalse(items.any { it.id == idToDelete })
    }

    @Test
    fun `clear should remove all records`() = runTest {
        TransferHistory.log(context, "file1", 100, "sent")
        TransferHistory.log(context, "file2", 200, "received")

        TransferHistory.clear(context)

        assertTrue(TransferHistory.items.value.isEmpty())
    }

    @Test
    fun `history should respect MAX_ENTRIES limit`() = runTest {
        repeat(210) { i ->
            TransferHistory.log(context, "file_$i", i.toLong(), "sent")
        }

        val items = TransferHistory.items.value
        assertEquals(200, items.size)
        // Should be the most recent 200
        assertEquals("file_209", items[0].name)
    }

    @Test
    fun `refresh should load items from storage`() = runTest {
        val json = """[{"id":"1","name":"stored","size":500,"timestamp":1000,"direction":"sent","status":"success"}]"""
        every { sharedPrefs.getString("transfers", null) } returns json

        TransferHistory.refresh(context)

        // Wait for the flow to be updated (polling as a workaround for hardcoded IO dispatcher)
        var items = TransferHistory.items.value
        val start = System.currentTimeMillis()
        while (items.isEmpty() && System.currentTimeMillis() - start < 2000) {
            kotlinx.coroutines.delay(50)
            items = TransferHistory.items.value
        }

        assertEquals(1, items.size)
        assertEquals("stored", items[0].name)
    }
}
