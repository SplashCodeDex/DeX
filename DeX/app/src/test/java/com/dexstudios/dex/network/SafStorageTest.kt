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

    @Test
    fun `buildRelativePath formats root and nested paths with trailing slash`() {
        assertEquals("Download/DeX/", SafStorage.buildRelativePath(null))
        assertEquals("Download/DeX/", SafStorage.buildRelativePath(""))
        assertEquals("Download/DeX/", SafStorage.buildRelativePath("single_file.pdf"))
        assertEquals("Download/DeX/subfolder/", SafStorage.buildRelativePath("subfolder/file.pdf"))
        assertEquals("Download/DeX/a/b/c/", SafStorage.buildRelativePath("/a/b/c/nested.txt/"))
    }

    @Test
    fun `createMediaStoreUri returns null on Android below Q`() {
        SafStorage.sdkInt = 28
        val uri = SafStorage.createMediaStoreUri(mockContext, "photo.jpg", null)
        org.junit.Assert.assertNull(uri)
    }

    @Test
    fun `createMediaStoreUri delegates to contentResolver on Android Q and above`() {
        SafStorage.sdkInt = 29
        val mockUri = mockk<Uri>()
        every { mockContentResolver.insert(any(), any()) } returns mockUri

        val uri = SafStorage.createMediaStoreUri(mockContext, "photo.jpg", null)
        assertEquals(mockUri, uri)
        verify { mockContentResolver.insert(any(), any()) }
    }

    @Test
    fun `saveUrisToSandbox calls finishMediaStoreUri to clear IS_PENDING on success`() {
        SafStorage.sdkInt = 29
        every { mockPrefs.getString("downloads_dex_uri", null) } returns null
        val sourceUri = mockk<Uri>()
        every { sourceUri.scheme } returns "content"
        every { sourceUri.path } returns "/file.png"

        val mediaStoreUri = mockk<Uri>()
        every { mockContentResolver.insert(any(), any()) } returns mediaStoreUri
        every { mockContentResolver.openInputStream(sourceUri) } answers { java.io.ByteArrayInputStream("payload".toByteArray()) }
        every { mockContentResolver.openOutputStream(mediaStoreUri) } answers { java.io.ByteArrayOutputStream() }
        every { mockContentResolver.update(mediaStoreUri, any(), null, null) } returns 1

        val result = SafStorage.saveUrisToSandbox(mockContext, listOf(sourceUri))
        assertEquals(1, result)
        // Must clear IS_PENDING = 0 via finishMediaStoreUri
        verify(exactly = 1) { mockContentResolver.update(mediaStoreUri, any(), null, null) }
    }

    @Test
    fun `saveUrisToSandbox does not count success when openOutputStream returns null`() {
        SafStorage.sdkInt = 29
        every { mockPrefs.getString("downloads_dex_uri", null) } returns null
        val sourceUri = mockk<Uri>()
        every { sourceUri.scheme } returns "content"
        every { sourceUri.path } returns "/file.png"

        val mediaStoreUri = mockk<Uri>()
        every { mockContentResolver.insert(any(), any()) } returns mediaStoreUri
        every { mockContentResolver.openInputStream(sourceUri) } answers { java.io.ByteArrayInputStream("payload".toByteArray()) }
        every { mockContentResolver.openOutputStream(mediaStoreUri) } returns null

        val result = SafStorage.saveUrisToSandbox(mockContext, listOf(sourceUri))
        assertEquals(0, result)
    }

    @Test
    fun `saveUrisToSandbox continues processing subsequent URIs when one throws exception`() {
        SafStorage.sdkInt = 29
        every { mockPrefs.getString("downloads_dex_uri", null) } returns null

        val failingUri = mockk<Uri>()
        every { failingUri.scheme } returns "content"
        every { failingUri.path } returns "/fail.png"
        every { mockContentResolver.openInputStream(failingUri) } throws SecurityException("Permission revoked")

        val validUri = mockk<Uri>()
        every { validUri.scheme } returns "content"
        every { validUri.path } returns "/success.png"

        val mediaStoreUri = mockk<Uri>()
        every { mockContentResolver.insert(any(), any()) } returns mediaStoreUri
        every { mockContentResolver.openInputStream(validUri) } answers { java.io.ByteArrayInputStream("valid".toByteArray()) }
        every { mockContentResolver.openOutputStream(mediaStoreUri) } answers { java.io.ByteArrayOutputStream() }
        every { mockContentResolver.update(mediaStoreUri, any(), null, null) } returns 1

        val result = SafStorage.saveUrisToSandbox(mockContext, listOf(failingUri, validUri))
        assertEquals(1, result)
    }

    @Test
    fun `saveUrisToSandbox falls back to MediaStore when SAF writeFile fails`() {
        SafStorage.sdkInt = 29
        every { mockPrefs.getString("downloads_dex_uri", null) } returns "content://mock/tree"

        io.mockk.mockkStatic(Uri::class)
        io.mockk.mockkStatic(android.provider.DocumentsContract::class)
        try {
            val mockTreeUri = mockk<Uri>()
            every { Uri.parse("content://mock/tree") } returns mockTreeUri
            every { android.provider.DocumentsContract.createDocument(any(), any(), any(), any()) } returns null

            val sourceUri = mockk<Uri>()
            every { sourceUri.scheme } returns "content"
            every { sourceUri.path } returns "/file.png"

            val mediaStoreUri = mockk<Uri>()
            every { mockContentResolver.insert(any(), any()) } returns mediaStoreUri
            every { mockContentResolver.openInputStream(sourceUri) } answers { java.io.ByteArrayInputStream("payload".toByteArray()) }
            every { mockContentResolver.openOutputStream(mediaStoreUri) } answers { java.io.ByteArrayOutputStream() }
            every { mockContentResolver.update(mediaStoreUri, any(), null, null) } returns 1

            val result = SafStorage.saveUrisToSandbox(mockContext, listOf(sourceUri))
            assertEquals(1, result)
            verify(exactly = 1) { mockContentResolver.insert(any(), any()) }
        } finally {
            io.mockk.unmockkStatic(android.provider.DocumentsContract::class)
            io.mockk.unmockkStatic(Uri::class)
        }
    }

    @Test
    fun `queryFileName sanitizes path traversal and special characters`() {
        val traversalUri = mockk<Uri>()
        every { traversalUri.scheme } returns "file"
        every { traversalUri.path } returns "/storage/emulated/0/Download/../../secret.txt"

        val name = SafStorage.queryFileName(mockContext, traversalUri)
        assertEquals("secret.txt", name)
    }

    @Test
    fun `sanitizeFileName strips path separators and illegal characters`() {
        assertEquals("test_file.txt", SafStorage.sanitizeFileName("test/file.txt"))
        assertEquals("my_doc.pdf", SafStorage.sanitizeFileName("..\\my:doc.pdf"))
        assertEquals("normal.jpg", SafStorage.sanitizeFileName("normal.jpg"))
        org.junit.Assert.assertTrue(SafStorage.sanitizeFileName("..").startsWith("SharedFile_"))
        org.junit.Assert.assertTrue(SafStorage.sanitizeFileName("   ").startsWith("SharedFile_"))
    }
}
