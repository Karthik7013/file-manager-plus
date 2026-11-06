package com.filemanagerplus.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** A provider-neutral entry exposed by the Storage Access Framework. */
data class StorageItem(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val mimeType: String?,
    val lastModified: Long = 0L,
    val sizeKnown: Boolean = false
)

data class StorageUsage(
    val totalBytes: Long,
    val fileCount: Int,
    val folderCount: Int,
    val unknownSizeCount: Int
)

enum class StorageSort { NAME, DATE, SIZE, TYPE }

class StorageRepository(private val context: Context) {
    /** Lists direct children of a SAF tree/document URI. Hidden files are opt-in. */
    fun list(
        documentUri: Uri,
        query: String = "",
        showHidden: Boolean = false,
        sort: StorageSort = StorageSort.NAME
    ): List<StorageItem> {
        val documentId = runCatching { DocumentsContract.getDocumentId(documentUri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(documentUri) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(documentUri, documentId)
        val result = mutableListOf<StorageItem>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: "Unnamed"
                if (!showHidden && name.startsWith('.')) continue
                if (query.isNotBlank() && !name.contains(query.trim(), ignoreCase = true)) continue
                val id = cursor.getString(idCol)
                val mime = cursor.getString(mimeCol)
                val sizeKnown = sizeCol >= 0 && !cursor.isNull(sizeCol)
                result += StorageItem(
                    uri = DocumentsContract.buildDocumentUriUsingTree(documentUri, id),
                    name = name,
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = if (sizeKnown) cursor.getLong(sizeCol) else 0L,
                    mimeType = mime,
                    lastModified = if (modifiedCol >= 0 && !cursor.isNull(modifiedCol)) cursor.getLong(modifiedCol) else 0L,
                    sizeKnown = sizeKnown
                )
            }
        }
        val directoriesFirst = compareByDescending<StorageItem> { it.isDirectory }
        return when (sort) {
            StorageSort.NAME -> result.sortedWith(directoriesFirst.thenBy { it.name.lowercase() })
            StorageSort.DATE -> result.sortedWith(directoriesFirst.thenByDescending { it.lastModified })
            StorageSort.SIZE -> result.sortedWith(directoriesFirst.thenByDescending { it.size })
            StorageSort.TYPE -> result.sortedWith(directoriesFirst.thenBy { it.mimeType.orEmpty() }.thenBy { it.name.lowercase() })
        }
    }

    fun createFolder(parentUri: Uri, name: String): Uri =
        createSafDocument(parentUri, DocumentsContract.Document.MIME_TYPE_DIR, validateDisplayName(name))

    fun createFile(parentUri: Uri, name: String, mimeType: String = "application/octet-stream"): Uri =
        createSafDocument(parentUri, mimeType, validateDisplayName(name))

    fun rename(uri: Uri, newName: String): Uri {
        val renamed = DocumentsContract.renameDocument(
            context.contentResolver, asDocumentUri(uri), validateDisplayName(newName)
        )
        return checkNotNull(renamed) { "Storage provider does not support renaming this item" }
    }

    fun delete(uri: Uri) {
        check(DocumentsContract.deleteDocument(context.contentResolver, asDocumentUri(uri))) {
            "Storage provider could not delete this item"
        }
    }

    /** Copies files and directories recursively using streams supplied by the SAF provider. */
    fun copy(sourceUri: Uri, destinationDirectoryUri: Uri): Uri {
        val source = asDocumentUri(sourceUri)
        val name = displayName(source)
        val mime = queryMimeType(source) ?: "application/octet-stream"
        val target = createSafDocument(destinationDirectoryUri, mime, name)
        try {
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                list(source, showHidden = true).forEach { child -> copy(child.uri, target) }
            } else {
                val input = context.contentResolver.openInputStream(source)
                    ?: error("Cannot read $name")
                val output = context.contentResolver.openOutputStream(target, "w")
                    ?: error("Cannot write $name")
                input.use { inStream -> output.use { outStream -> inStream.copyTo(outStream) } }
            }
            return target
        } catch (failure: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
            throw failure
        }
    }

    /** Uses a provider-native move when possible, otherwise performs copy-then-delete. */
    fun move(sourceUri: Uri, sourceParentUri: Uri, destinationDirectoryUri: Uri): Uri =
        runCatching {
            DocumentsContract.moveDocument(
                context.contentResolver, asDocumentUri(sourceUri),
                asDocumentUri(sourceParentUri), asDocumentUri(destinationDirectoryUri)
            ) ?: error("Provider declined native move")
        }.getOrElse {
            val copied = copy(sourceUri, destinationDirectoryUri)
            delete(sourceUri)
            copied
        }

    /** Compares provider document identities (rather than tree/document URI string forms). */
    fun sameDocument(first: Uri, second: Uri): Boolean {
        if (first.authority != second.authority) return false
        fun identity(uri: Uri): String = runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(uri) }
        return identity(first) == identity(second)
    }

    /** Calculates a recursive summary on the caller's background dispatcher. */
    fun calculateUsage(rootUri: Uri): StorageUsage {
        val pending = ArrayDeque<Uri>()
        val visited = mutableSetOf<String>()
        pending.add(asDocumentUri(rootUri))
        var totalBytes = 0L
        var fileCount = 0
        var folderCount = 0
        var unknownSizeCount = 0

        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            val directoryKey = directory.toString()
            if (!visited.add(directoryKey)) continue
            val children = list(directory, showHidden = true)
            for (child in children) {
                if (child.isDirectory) {
                    folderCount++
                    pending.add(child.uri)
                } else {
                    fileCount++
                    if (child.sizeKnown) totalBytes += child.size else unknownSizeCount++
                }
            }
        }
        return StorageUsage(totalBytes, fileCount, folderCount, unknownSizeCount)
    }

    fun displayName(uri: Uri): String = runCatching {
        val documentUri = asDocumentUri(uri)
        context.contentResolver.query(
            documentUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: "Selected folder"

    private fun createSafDocument(parentUri: Uri, mimeType: String, name: String): Uri {
        val created = DocumentsContract.createDocument(
            context.contentResolver, asDocumentUri(parentUri), mimeType, name
        )
        return checkNotNull(created) { "Storage provider could not create $name" }
    }

    private fun queryMimeType(uri: Uri): String? = context.contentResolver.query(
        asDocumentUri(uri), arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    /** OpenDocumentTree returns a tree URI; child entries are document URIs. */
    private fun asDocumentUri(uri: Uri): Uri = runCatching {
        DocumentsContract.getDocumentId(uri)
        uri
    }.getOrElse {
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
    }
}
