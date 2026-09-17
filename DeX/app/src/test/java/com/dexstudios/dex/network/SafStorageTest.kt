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

    @org.junit.After
    fun tearDown() {
        SafStorage.sdkInt = android.os.Build.VERSION.SDK_INT
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

    @Test
    fun `createDocumentWithPath returns null on empty relativePath`() {
        val treeUri = mockk<Uri>()
        val result = SafStorage.createDocumentWithPath(mockContext, treeUri, "")
        org.junit.Assert.assertNull(result)
    }

    @Test
    fun `createDocumentWithPath reuses existing directory without creating duplicate`() {
        io.mockk.mockkStatic(android.provider.DocumentsContract::class)
        try {
            val treeUri = mockk<Uri>()
            val subDirUri = mockk<Uri>()
            val finalDocUri = mockk<Uri>()
            val childrenUri = mockk<Uri>()

            every { android.provider.DocumentsContract.getTreeDocumentId(treeUri) } returns "root_doc_id"
            every { android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, "root_doc_id") } returns childrenUri

            val cursor = mockk<android.database.Cursor>(relaxed = true)
            every { cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID) } returns 0
            every { cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME) } returns 1
            every { cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE) } returns 2
            every { cursor.moveToNext() } returnsMany listOf(true, false)
            every { cursor.getString(0) } returns "sub_doc_id"
            every { cursor.getString(1) } returns "subfolder"
            every { cursor.getString(2) } returns android.provider.DocumentsContract.Document.MIME_TYPE_DIR

            every { mockContentResolver.query(childrenUri, any(), null, null, null) } returns cursor
            every { android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, "sub_doc_id") } returns subDirUri

            every {
                android.provider.DocumentsContract.createDocument(
                    mockContentResolver, subDirUri, "application/octet-stream", "test.txt"
                )
            } returns finalDocUri

            val result = SafStorage.createDocumentWithPath(mockContext, treeUri, "subfolder/test.txt")

            assertEquals(finalDocUri, result)
            verify(exactly = 0) {
                android.provider.DocumentsContract.createDocument(any(), any(), android.provider.DocumentsContract.Document.MIME_TYPE_DIR, any())
            }
        } finally {
            io.mockk.unmockkStatic(android.provider.DocumentsContract::class)
        }
    }

    @Test
    fun `createDocumentWithPath creates directory when it does not exist`() {
        io.mockk.mockkStatic(android.provider.DocumentsContract::class)
        try {
            val treeUri = mockk<Uri>()
            val newDirUri = mockk<Uri>()
            val finalDocUri = mockk<Uri>()
            val childrenUri = mockk<Uri>()

            every { android.provider.DocumentsContract.getTreeDocumentId(treeUri) } returns "root_doc_id"
            every { android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, "root_doc_id") } returns childrenUri

            val cursor = mockk<android.database.Cursor>(relaxed = true)
            every { cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID) } returns 0
            every { cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME) } returns 1
            every { cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE) } returns 2
            every { cursor.moveToNext() } returns false

            every { mockContentResolver.query(childrenUri, any(), null, null, null) } returns cursor

            every {
                android.provider.DocumentsContract.createDocument(
                    mockContentResolver, treeUri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR, "newfolder"
                )
            } returns newDirUri
            every { android.provider.DocumentsContract.getDocumentId(newDirUri) } returns "new_dir_id"

            every {
                android.provider.DocumentsContract.createDocument(
                    mockContentResolver, newDirUri, "application/octet-stream", "file.pdf"
                )
            } returns finalDocUri

            val result = SafStorage.createDocumentWithPath(mockContext, treeUri, "newfolder/file.pdf")

            assertEquals(finalDocUri, result)
            verify(exactly = 1) {
                android.provider.DocumentsContract.createDocument(mockContentResolver, treeUri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR, "newfolder")
            }
        } finally {
            io.mockk.unmockkStatic(android.provider.DocumentsContract::class)
        }
    }

    @Test
    fun `listTreeFiles queries children using subfolder docId and collects files`() {
        io.mockk.mockkStatic(android.provider.DocumentsContract::class)
        try {
            val treeUri = mockk<Uri>()
            val rootChildrenUri = mockk<Uri>()
            val subChildrenUri = mockk<Uri>()
            val fileDocUri = mockk<Uri>()

            every { android.provider.DocumentsContract.getTreeDocumentId(treeUri) } returns "root_doc_id"
            every { android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, "root_doc_id") } returns rootChildrenUri
            every { android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, "child_dir_id") } returns subChildrenUri

            val rootCursor = mockk<android.database.Cursor>(relaxed = true)
            every { rootCursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID) } returns 0
            every { rootCursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME) } returns 1
            every { rootCursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE) } returns 2
            every { rootCursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE) } returns 3
            every { rootCursor.moveToNext() } returnsMany listOf(true, false)
            every { rootCursor.getString(0) } returns "child_dir_id"
            every { rootCursor.getString(1) } returns "docs"
            every { rootCursor.getString(2) } returns android.provider.DocumentsContract.Document.MIME_TYPE_DIR
            every { rootCursor.getLong(3) } returns 0L

            val subCursor = mockk<android.database.Cursor>(relaxed = true)
            every { subCursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID) } returns 0
            every { subCursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME) } returns 1
            every { subCursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE) } returns 2
            every { subCursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE) } returns 3
            every { subCursor.moveToNext() } returnsMany listOf(true, false)
            every { subCursor.getString(0) } returns "file_doc_id"
            every { subCursor.getString(1) } returns "readme.txt"
            every { subCursor.getString(2) } returns "text/plain"
            every { subCursor.getLong(3) } returns 100L

            every { mockContentResolver.query(rootChildrenUri, any(), null, null, null) } returns rootCursor
            every { mockContentResolver.query(subChildrenUri, any(), null, null, null) } returns subCursor
            every { android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, "file_doc_id") } returns fileDocUri

            val files = SafStorage.listTreeFiles(mockContext, treeUri)

            assertEquals(1, files.size)
            assertEquals("docs/readme.txt", files[0].second)
            assertEquals(100L, files[0].third)
            assertEquals(fileDocUri, files[0].first)
        } finally {
            io.mockk.unmockkStatic(android.provider.DocumentsContract::class)
        }
    }

    @Test
    fun `finishMediaStoreUri updates IS_PENDING to 0`() {
        SafStorage.sdkInt = 29
        val mockUri = mockk<Uri>()
        every { mockContentResolver.update(mockUri, any(), null, null) } returns 1

        SafStorage.finishMediaStoreUri(mockContext, mockUri)

        verify { mockContentResolver.update(mockUri, any(), null, null) }
    }
}
