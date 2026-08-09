package com.dexstudios.dex.network

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SafStorageTest {

    private val mockContext = mockk<Context>()
    private val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    private val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val mockContentResolver = mockk<ContentResolver>(relaxed = true)

    @Before
    fun setUp() {
        every { mockContext.getSharedPreferences("dex_saf_prefs", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockContext.contentResolver } returns mockContentResolver
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
    }

    @Test
    fun `getDownloadsDexUri returns null when preference is empty`() {
        every { mockPrefs.getString("downloads_dex_uri", null) } returns null

        assertEquals(null, SafStorage.getDownloadsDexUri(mockContext))
    }

    @Test
    fun `setDownloadsDexUri persists the tree uri string`() {
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns "content://com.android.externalstorage.documents/tree/primary%3ADownloads%2FDeX"

        SafStorage.setDownloadsDexUri(mockContext, mockUri)

        val slotJson = slot<String>()
        verify { mockEditor.putString("downloads_dex_uri", capture(slotJson)) }
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3ADownloads%2FDeX", slotJson.captured)
    }
}
