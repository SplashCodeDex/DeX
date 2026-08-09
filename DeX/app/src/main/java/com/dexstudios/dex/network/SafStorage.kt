package com.dexstudios.dex.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.content.edit
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

object SafStorage {
    private const val PREFS = "dex_saf_prefs"
    private const val KEY_DOWNLOADS_DEX_URI = "downloads_dex_uri"
    private const val KEY_GRANTED_FOLDERS = "granted_folders"

    // --- Downloads/DeX folder grant (incoming transfers) ---

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
        val intent = Intent(context, com.dexstudios.dex.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("REQUEST_DOWNLOADS_DEX_GRANT", true)
        }
        context.startActivity(intent)
    }

    fun writeToDownloadsDex(context: Context, fileName: String, input: InputStream): Boolean {
        val dirUri = getDownloadsDexUri(context) ?: return false
        return writeFile(context, dirUri, fileName, input)
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

    fun openOutputStream(context: Context, dirUri: Uri, fileName: String): OutputStream? {
        return try {
            val doc = createDocumentUri(context, dirUri, fileName) ?: return null
            context.contentResolver.openOutputStream(doc)
        } catch (_: Exception) {
            null
        }
    }

    // --- File explorer folder grants (opt-in, user-picked) ---

    fun getGrantedFolders(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_GRANTED_FOLDERS, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            json.keys().forEach { key -> result[key] = json.getString(key) }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun addGrantedFolder(context: Context, name: String, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = getGrantedFolders(context).toMutableMap()
        current[name] = uri.toString()
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k, v) }
        prefs.edit { putString(KEY_GRANTED_FOLDERS, json.toString()) }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }

    fun removeGrantedFolder(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = getGrantedFolders(context).toMutableMap()
        current.remove(name)
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k, v) }
        prefs.edit { putString(KEY_GRANTED_FOLDERS, json.toString()) }
    }

    // --- SAF listing / reading for the file explorer ---

    fun listChildren(context: Context, treeUri: Uri): List<BrowseFileDto> {
        val result = mutableListOf<BrowseFileDto>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        context.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val mime = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                result.add(BrowseFileDto(name = name, isDirectory = isDir, size = size, path = docUri.toString()))
            }
        }
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun readDocument(context: Context, docUri: Uri, out: OutputStream): Boolean {
        return try {
            context.contentResolver.openInputStream(docUri)?.use { input -> input.copyTo(out) }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readDocumentBytes(context: Context, docUri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(docUri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }
}
