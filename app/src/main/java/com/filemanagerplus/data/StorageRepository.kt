package com.filemanagerplus.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date

/** A file-system entry exposed through direct file access (MANAGE_EXTERNAL_STORAGE). */
data class StorageItem(
    val path: String,
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

    /** Returns available storage roots (e.g. internal storage, SD card). */
    fun listRoots(): List<StorageItem> {
        val result = mutableListOf<StorageItem>()
        File.listRoots().forEach { root ->
            val name = when {
                root.path == Environment.getExternalStorageDirectory().path -> "Internal Storage"
                root.path.contains("emulated") -> "Internal Storage"
                else -> root.name.ifBlank { root.path }
            }
            result += StorageItem(
                path = root.path,
                name = name,
                isDirectory = true,
                size = 0L,
                mimeType = null,
                sizeKnown = false
            )
        }
        return result
    }

    /** Lists direct children of a directory path. Hidden files are opt-in. */
    fun list(
        path: String,
        query: String = "",
        showHidden: Boolean = false,
        sort: StorageSort = StorageSort.NAME
    ): List<StorageItem> {
        val dir = File(path)
        val files = dir.listFiles() ?: return emptyList()
        val result = mutableListOf<StorageItem>()
        files.forEach { file ->
            val name = file.name
            if (!showHidden && name.startsWith('.')) return@forEach
            if (query.isNotBlank() && !name.contains(query.trim(), ignoreCase = true)) return@forEach
            val mime = mimeTypeFor(file)
            result += StorageItem(
                path = file.absolutePath,
                name = name,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0L else file.length(),
                mimeType = mime,
                lastModified = file.lastModified(),
                sizeKnown = file.isFile && file.exists()
            )
        }
        val directoriesFirst = compareByDescending<StorageItem> { it.isDirectory }
        return when (sort) {
            StorageSort.NAME -> result.sortedWith(directoriesFirst.thenBy { it.name.lowercase() })
            StorageSort.DATE -> result.sortedWith(directoriesFirst.thenByDescending { it.lastModified })
            StorageSort.SIZE -> result.sortedWith(directoriesFirst.thenByDescending { it.size })
            StorageSort.TYPE -> result.sortedWith(directoriesFirst.thenBy { it.mimeType.orEmpty() }.thenBy { it.name.lowercase() })
        }
    }

    fun createFolder(parentPath: String, name: String): String {
        val validated = validateDisplayName(name)
        val newDir = File(File(parentPath), validated)
        if (!newDir.mkdir()) {
            error("Storage provider could not create $validated")
        }
        return newDir.absolutePath
    }

    fun createFile(parentPath: String, name: String, mimeType: String = "application/octet-stream"): String {
        val validated = validateDisplayName(name)
        val newFile = File(File(parentPath), validated)
        if (!newFile.createNewFile()) {
            error("Storage provider could not create $validated")
        }
        return newFile.absolutePath
    }

    fun rename(path: String, newName: String): String {
        val validated = validateDisplayName(newName)
        val file = File(path)
        val parent = file.parentFile
        if (parent == null) error("Cannot rename $path: no parent")
        val target = File(parent, validated)
        if (!file.renameTo(target)) {
            error("Storage provider does not support renaming this item")
        }
        return target.absolutePath
    }

    fun delete(path: String) {
        val file = File(path)
        if (!deleteRecursively(file)) {
            error("Storage provider could not delete this item")
        }
    }

    private fun deleteRecursively(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                if (!deleteRecursively(child)) return false
            }
        }
        return file.delete()
    }

    /** Copies files and directories recursively using direct file I/O. */
    fun copy(sourcePath: String, destinationDirectoryPath: String): String {
        val source = File(sourcePath)
        val name = source.name
        val target = File(File(destinationDirectoryPath), name)
        try {
            if (source.isDirectory) {
                if (!target.mkdirs()) error("Cannot create directory $name")
                source.listFiles()?.forEach { child ->
                    copy(child.absolutePath, target.absolutePath)
                }
            } else {
                if (!target.parentFile.mkdirs() && !target.parentFile.isDirectory) {
                    error("Cannot create parent directory for $name")
                }
                copyFile(source, target)
            }
            return target.absolutePath
        } catch (failure: Exception) {
            runCatching { delete(target.absolutePath) }
            throw failure
        }
    }

    private fun copyFile(source: File, target: File) {
        java.nio.file.Files.copy(
            source.toPath(),
            target.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
    }

    private fun copyStream(input: InputStream, output: OutputStream, bufferSize: Int = 8192) {
        val buffer = ByteArray(bufferSize)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
    }

    /** Moves a file or directory. Falls back to copy-then-delete if rename fails. */
    fun move(sourcePath: String, destinationDirectoryPath: String): String {
        val source = File(sourcePath)
        val target = File(File(destinationDirectoryPath), source.name)
        if (source.renameTo(target)) {
            return target.absolutePath
        }
        val copied = copy(sourcePath, destinationDirectoryPath)
        delete(sourcePath)
        return copied
    }

    /** Copies a file to a SAF destination URI using ContentResolver. */
    fun copyToUri(sourcePath: String, destUri: Uri, resolver: ContentResolver): String {
        val source = File(sourcePath)
        require(source.exists() && source.isFile) { "Source file does not exist: $sourcePath" }
        val destFile = DocumentFile.fromTreeUri(resolver, destUri)
            ?: error("Cannot access destination URI: $destUri")
        val targetFile = destFile.createFile("application/octet-stream", source.name)
            ?: error("Cannot create file in destination: $source.name")
        val output = resolver.openOutputStream(targetFile.uri)
            ?: error("Cannot open output stream for destination")
        try {
            source.inputStream().use { input ->
                copyStream(input, output)
            }
        } finally {
            output.close()
        }
        return targetFile.uri.toString()
    }

    /** Copies a directory recursively to a SAF destination URI. */
    fun copyDirectoryToUri(sourcePath: String, destUri: Uri, resolver: ContentResolver): String {
        val source = File(sourcePath)
        require(source.exists() && source.isDirectory) { "Source directory does not exist: $sourcePath" }
        val destFile = DocumentFile.fromTreeUri(context, destUri)
            ?: error("Cannot access destination URI: $destUri")
        val targetDir = destFile.createDirectory(source.name)
            ?: error("Cannot create directory in destination: $source.name")
        source.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                copyDirectoryToUri(child.absolutePath, targetDir.uri, resolver)
            } else {
                copyToUri(child.absolutePath, targetDir.uri, resolver)
            }
        }
        return targetDir.uri.toString()
    }

    /** Moves a file to a SAF destination URI: copy then delete source. */
    fun moveToUri(sourcePath: String, destUri: Uri, resolver: ContentResolver): String {
        val source = File(sourcePath)
        val result = if (source.isDirectory) {
            copyDirectoryToUri(sourcePath, destUri, resolver)
        } else {
            copyToUri(sourcePath, destUri, resolver)
        }
        delete(sourcePath)
        return result
    }

    /** Checks whether a source path would be moved/copied into itself or a descendant. */
    fun isPathWithin(sourcePath: String, potentialParentPath: String): Boolean {
        val sourceFile = File(sourcePath).canonicalFile
        val parentFile = File(potentialParentPath).canonicalFile
        return sourceFile.path.startsWith(parentFile.path + File.separator)
    }

    /** Checks whether a source path would be moved/copied into itself or a descendant under a SAF URI. */
    fun isPathWithinUri(sourcePath: String, destUri: Uri, resolver: ContentResolver): Boolean {
        val sourceFile = File(sourcePath).canonicalFile
        val destFile = DocumentFile.fromTreeUri(context, destUri)
            ?: return false
        val destPath = destFile.uri.toString()
        // If dest URI points to a path that contains the source, the source is within dest
        return destPath.contains(sourceFile.path) || sourceFile.path.startsWith(destPath)
    }

    /** Checks whether a destination path already contains an item with the same name. */
    fun destinationExists(destinationDirectoryPath: String, name: String): Boolean {
        return File(File(destinationDirectoryPath), name).exists()
    }

    /** Checks whether a SAF destination already contains an item with the same name. */
    fun destinationExistsUri(destUri: Uri, resolver: ContentResolver, name: String): Boolean {
        val destFile = DocumentFile.fromTreeUri(context, destUri)
            ?: return false
        return destFile.listFiles()?.any { file: DocumentFile -> file.name == name } == true
    }

    /** Checks whether two paths refer to the same file. */
    fun samePath(first: String, second: String): Boolean {
        return File(first).canonicalFile == File(second).canonicalFile
    }

    /** Calculates a recursive summary on the caller's background dispatcher. */
    fun calculateUsage(rootPath: String): StorageUsage {
        val pending = ArrayDeque<File>()
        val visited = mutableSetOf<String>()
        pending.add(File(rootPath))
        var totalBytes = 0L
        var fileCount = 0
        var folderCount = 0
        var unknownSizeCount = 0

        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            val directoryKey = directory.canonicalPath
            if (!visited.add(directoryKey)) continue
            val children = directory.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    folderCount++
                    pending.add(child)
                } else {
                    fileCount++
                    if (child.exists()) totalBytes += child.length() else unknownSizeCount++
                }
            }
        }
        return StorageUsage(totalBytes, fileCount, folderCount, unknownSizeCount)
    }

    fun displayName(path: String): String {
        return File(path).name.ifBlank { "Selected folder" }
    }

    private fun mimeTypeFor(file: File): String? {
        if (file.isDirectory) return null
        val name = file.name.lowercase()
        return when {
            name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".aac") ||
            name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg") -> "audio/*"
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
            name.endsWith(".avi") -> "video/*"
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
            name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp") -> "image/*"
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".zip") -> "application/zip"
            name.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun validateDisplayName(name: String): String {
        val cleaned = name.trim()
        require(cleaned.isNotEmpty() && cleaned != "." && cleaned != ".." &&
            !cleaned.contains('/') && !cleaned.contains('\\')) {
            "Enter a valid file or folder name"
        }
        return cleaned
    }

    companion object {
        /** Whether the app has been granted MANAGE_EXTERNAL_STORAGE (API 29+). */
        fun hasAllFilesAccess(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        }
    }
}
