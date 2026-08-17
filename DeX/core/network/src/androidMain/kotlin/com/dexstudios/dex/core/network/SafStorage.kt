package com.dexstudios.dex.core.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.content.edit
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream

object SafStorage {
    private const val PREFS = "dex_saf_prefs"
    private const val KEY_DOWNLOADS_DEX_URI = "downloads_dex_uri"
    private const val KEY_SHARED_FOLDERS = "dex_shared_folders"

    // --- Downloads/DeX folder grant (incoming transfers) ---

    fun createMediaStoreUri(context: Context, fileName: String, relativePath: String? = null): Uri? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return null

        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")

            val base = "Download/DeX"
            val subPath = if (!relativePath.isNullOrBlank()) {
                val parts = relativePath.trim('/').split('/').filter { it.isNotBlank() && it != ".." }
                if (parts.size > 1) {
                    "/" + parts.dropLast(1).joinToString("/")
                } else ""
            } else ""

            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, base + subPath)
        }

        return try {
            resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: Exception) {
            null
        }
    }

    fun getDownloadsDexUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = prefs.getString(KEY_DOWNLOADS_DEX_URI, null) ?: return null
        return s.toUri()
    }

    fun setDownloadsDexUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_DOWNLOADS_DEX_URI, uri.toString()) }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }

    fun promptForDownloadsDexGrant(context: Context) {
        val intent = Intent(context, Class.forName("com.dexstudios.dex.MainActivity")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("REQUEST_DOWNLOADS_DEX_GRANT", true)
        }
        context.startActivity(intent)
    }

    // --- Shared folders (PC File Explorer: browse + pull) ---

    /** The tree URIs the user has granted for remote browsing, in order. */
    fun listSharedFolderUris(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SHARED_FOLDERS, null)
            ?.let { raw ->
                try { kotlinx.serialization.json.Json.decodeFromString<List<String>>(raw) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
    }

    /** Grants [uri] (a tree URI) as a browsable shared folder if it is not already one. */
    fun addSharedFolder(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
        val current = listSharedFolderUris(context)
        if (uri.toString() in current) return
        prefs.edit { putString(KEY_SHARED_FOLDERS, kotlinx.serialization.json.Json.encodeToString<List<String>>(current + uri.toString())) }
    }

    /** Prompts the user to pick a folder to expose to the PC's File Explorer. */
    fun promptForSharedFolderGrant(context: Context) {
        val intent = Intent(context, Class.forName("com.dexstudios.dex.MainActivity")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("REQUEST_SHARED_FOLDER_GRANT", true)
        }
        context.startActivity(intent)
    }

    /**
     * Lists the immediate children of [folder] (a tree URI for a root, or a nested
     * tree-document URI for a subfolder). The incoming [folder] must be one of the
     * granted shared-folder URIs or a document URI beneath one.
     */
    fun listFolderEntries(context: Context, folder: Uri): List<FolderEntryDto> {
        val (treeUri, parentDocId) = parseTreeLocation(folder)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val entries = mutableListOf<FolderEntryDto>()
        context.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val mime = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                entries.add(FolderEntryDto(name = name, uri = docUri, isDirectory = isDir, size = size))
            }
        }
        return entries
    }

    /** Splits any shared-folder location into (treeUri, parentDocumentId). */
    private fun parseTreeLocation(uri: Uri): Pair<Uri, String> {
        val asString = uri.toString()
        val documentMarker = "/document/"
        return if (asString.contains(documentMarker)) {
            val treeUri = asString.substringBefore(documentMarker).toUri()
            val parentDocId = asString.substringAfter(documentMarker)
            treeUri to parentDocId
        } else {
            uri to (runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: "")
        }
    }

    /** Human-readable display name for a shared-folder tree URI, falling back to "Folder". */
    fun sharedFolderName(context: Context, treeUri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            var name: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
            if (name.isNullOrBlank()) "Folder" else name
        } catch (_: Exception) {
            "Folder"
        }
    }

    fun writeFile(context: Context, dirUri: Uri, fileName: String, input: InputStream): Boolean {
        return try {
            val doc = DocumentsContract.createDocument(
                context.contentResolver, dirUri, "application/octet-stream", fileName
            )
            if (doc != null) {
                context.contentResolver.openOutputStream(doc)?.use { out -> input.copyTo(out) }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun createDocumentUri(context: Context, dirUri: Uri, fileName: String): Uri? {
        return try {
            DocumentsContract.createDocument(
                context.contentResolver, dirUri, "application/octet-stream", fileName
            )
        } catch (_: Exception) {
            null
        }
    }

    // --- Folder bundles: relative paths with intermediate directory creation ---

    /**
     * Creates a document at [relativePath] inside [treeUri], creating any intermediate
     * directories. Path traversal ("..") is stripped. Returns null on failure.
     */
    fun createDocumentWithPath(context: Context, treeUri: Uri, relativePath: String): Uri? {
        val parts = relativePath.trim('/').split('/')
            .filter { it.isNotBlank() && it != ".." }
        if (parts.isEmpty()) return null
        var current = treeUri
        for (segment in parts.dropLast(1)) {
            current = createDirectory(context, current, segment) ?: return null
        }
        return createDocumentUri(context, current, parts.last())
    }

    private fun createDirectory(context: Context, parent: Uri, name: String): Uri? {
        return try {
            DocumentsContract.createDocument(
                context.contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Recursively lists every file under [treeUri] as (documentUri, relativePath, size). */
    fun listTreeFiles(context: Context, treeUri: Uri): List<Triple<Uri, String, Long>> {
        val result = mutableListOf<Triple<Uri, String, Long>>()
        fun walk(dirUri: Uri, prefix: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                dirUri, DocumentsContract.getTreeDocumentId(dirUri)
            )
            context.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    val mime = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(dirUri, docId)
                    val rel = if (prefix.isEmpty()) name else "$prefix/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) walk(docUri, rel)
                    else result.add(Triple(docUri, rel, size))
                }
            }
        }
        walk(treeUri, "")
        return result
    }
}

