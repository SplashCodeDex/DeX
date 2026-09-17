package com.dexstudios.dex.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.content.edit
import android.provider.DocumentsContract
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream

object SafStorage {
    private const val PREFS = "dex_saf_prefs"
    private const val KEY_DOWNLOADS_DEX_URI = "downloads_dex_uri"
    private const val KEY_SHARED_FOLDERS = "dex_shared_folders"

    // --- Downloads/DeX folder grant (incoming transfers) ---

    @androidx.annotation.VisibleForTesting
    var sdkInt: Int = android.os.Build.VERSION.SDK_INT

    internal fun buildRelativePath(relativePath: String? = null): String {
        val base = "Download/DeX"
        val subPath = if (!relativePath.isNullOrBlank()) {
            val parts = relativePath.trim('/').split('/').filter { it.isNotBlank() && it != ".." }
            if (parts.size > 1) {
                "/" + parts.dropLast(1).joinToString("/")
            } else ""
        } else ""
        return (base + subPath).trimEnd('/') + "/"
    }

    fun createMediaStoreUri(context: Context, fileName: String, relativePath: String? = null): Uri? {
        if (sdkInt < android.os.Build.VERSION_CODES.Q) return null

        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, buildRelativePath(relativePath))
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }

        return try {
            resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clears the `IS_PENDING` flag on [uri] once bytes have finished streaming,
     * publishing the completed file to media scanners and external applications.
     */
    fun finishMediaStoreUri(context: Context, uri: Uri?) {
        if (uri == null || sdkInt < android.os.Build.VERSION_CODES.Q) return
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
        }
        try {
            context.contentResolver.update(uri, contentValues, null, null)
        } catch (e: Exception) {
            Timber.w(e, "SafStorage: Failed to clear IS_PENDING on $uri")
        }
    }

    fun getDownloadsDexUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = prefs.getString(KEY_DOWNLOADS_DEX_URI, null) ?: return null
        return try { s.toUri() } catch (_: Exception) { null }
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
        val intent = Intent(context, com.dexstudios.dex.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("REQUEST_DOWNLOADS_DEX_GRANT", true)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.e(it, "SafStorage: cannot launch grant activity") }
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
        val intent = Intent(context, com.dexstudios.dex.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("REQUEST_SHARED_FOLDER_GRANT", true)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.e(it, "SafStorage: cannot launch grant activity") }
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

    fun deleteUri(context: Context, uri: Uri?) {
        if (uri == null) return
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (_: Exception) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
        }
    }

    fun writeFile(context: Context, dirUri: Uri, fileName: String, input: InputStream): Boolean {
        val safeName = sanitizeFileName(fileName)
        val doc = try {
            DocumentsContract.createDocument(
                context.contentResolver, dirUri, "application/octet-stream", safeName
            )
        } catch (e: Exception) {
            Timber.w(e, "SafStorage: Cannot create document in $dirUri for $safeName")
            null
        } ?: return false

        return try {
            val out = context.contentResolver.openOutputStream(doc)
            if (out != null) {
                out.use { input.copyTo(it) }
                true
            } else {
                deleteUri(context, doc)
                false
            }
        } catch (e: Exception) {
            Timber.w(e, "SafStorage: Failed writing to document $doc")
            deleteUri(context, doc)
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
     * Creates a document at [relativePath] inside [treeUri], creating intermediate
     * directories only when they do not already exist. Reuses existing directories so
     * multiple files in the same subfolder do not produce duplicate "folder (1)" names.
     * Path traversal ("..") is stripped. Returns null on failure.
     */
    fun createDocumentWithPath(context: Context, treeUri: Uri, relativePath: String): Uri? {
        val parts = relativePath.trim('/').split('/')
            .filter { it.isNotBlank() && it != ".." }
        if (parts.isEmpty()) return null

        var currentParentUri = treeUri
        var currentDocId = DocumentsContract.getTreeDocumentId(treeUri)

        for (segment in parts.dropLast(1)) {
            val existing = findChildDirectory(context, treeUri, currentDocId, segment)
            if (existing != null) {
                currentParentUri = existing.first
                currentDocId = existing.second
            } else {
                val newDir = createDirectory(context, currentParentUri, segment) ?: return null
                currentParentUri = newDir
                currentDocId = try {
                    DocumentsContract.getDocumentId(newDir)
                } catch (_: Exception) {
                    DocumentsContract.getTreeDocumentId(newDir)
                }
            }
        }
        return createDocumentUri(context, currentParentUri, parts.last())
    }

    private fun findChildDirectory(context: Context, treeUri: Uri, parentDocId: String, name: String): Pair<Uri, String>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idIdx < 0 || nameIdx < 0 || mimeIdx < 0) return@use null

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIdx)
                    val mime = cursor.getString(mimeIdx)
                    if (displayName == name && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val docId = cursor.getString(idIdx)
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        return@use Pair(docUri, docId)
                    }
                }
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "SafStorage: cannot query child directory $name under $parentDocId")
            null
        }
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
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)

        fun walk(parentDocId: String, prefix: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                if (idIdx < 0 || nameIdx < 0 || mimeIdx < 0) return@use

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx) ?: ""
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val rel = if (prefix.isEmpty()) name else "$prefix/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(docId, rel)
                    } else {
                        result.add(Triple(docUri, rel, size))
                    }
                }
            }
        }
        walk(rootDocId, "")
        return result
    }

    // --- Share-target sandbox fallback ---

    internal fun sanitizeFileName(name: String?): String {
        if (name.isNullOrBlank()) return "SharedFile_${System.currentTimeMillis()}"
        val withoutTraversal = name.replace("..", "").replace(Regex("[/\\\\:;*?\"<>|]"), "_").trim()
        val clean = withoutTraversal.trimStart('.', '_', ' ').trimEnd('.', ' ')
        return if (clean.isBlank()) {
            "SharedFile_${System.currentTimeMillis()}"
        } else {
            clean
        }
    }

    /** Resolves a display name for [uri], falling back to a generated name. */
    fun queryFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "SafStorage: cannot query display name for $uri")
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return sanitizeFileName(result)
    }

    /**
     * Persists [uris] into the Downloads/DeX sandbox (SAF tree when granted,
     * MediaStore otherwise). Returns the number of successfully saved files so
     * callers can surface partial failures.
     */
    fun saveUrisToSandbox(context: Context, uris: List<Uri>): Int {
        val dirUri = getDownloadsDexUri(context)
        var successCount = 0
        for (uri in uris) {
            try {
                val fileName = queryFileName(context, uri)
                var written = false

                // 1. Attempt SAF directory write when granted
                if (dirUri != null) {
                    val safOk = try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            writeFile(context, dirUri, fileName, input)
                        } ?: false
                    } catch (e: Exception) {
                        Timber.w(e, "SafStorage: Failed writing to SAF directory for $fileName")
                        false
                    }
                    if (safOk) {
                        written = true
                    }
                }

                // 2. Fallback to MediaStore if SAF is unconfigured, revoked, or failed
                if (!written) {
                    val mediaOk = try {
                        val mediaUri = createMediaStoreUri(context, fileName)
                        if (mediaUri != null) {
                            try {
                                val out = context.contentResolver.openOutputStream(mediaUri)
                                if (out != null) {
                                    val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                                        out.use { outStream -> input.copyTo(outStream) }
                                        true
                                    } ?: false
                                    if (copied) {
                                        finishMediaStoreUri(context, mediaUri)
                                        true
                                    } else {
                                        deleteUri(context, mediaUri)
                                        false
                                    }
                                } else {
                                    deleteUri(context, mediaUri)
                                    false
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "SafStorage: Failed writing to MediaStore for $fileName")
                                deleteUri(context, mediaUri)
                                false
                            }
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "SafStorage: MediaStore fallback failed for $fileName")
                        false
                    }
                    if (mediaOk) {
                        written = true
                    }
                }

                if (written) {
                    successCount++
                }
            } catch (e: Exception) {
                Timber.w(e, "SafStorage: Error processing URI $uri for sandbox")
            }
        }
        return successCount
    }
}
