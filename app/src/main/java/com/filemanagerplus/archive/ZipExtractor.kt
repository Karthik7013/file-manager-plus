package com.filemanagerplus.archive

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Extracts ZIP content into an app-private destination directory.
 * SAF destination support and progress/cancellation are planned next.
 */
class ZipExtractor(private val context: Context) {
    fun extractToAppCache(zipUri: Uri, folderName: String): File {
        val base = File(context.cacheDir, "extracted").apply { mkdirs() }.canonicalFile
        val safeFolder = folderName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(base, safeFolder).apply { mkdirs() }.canonicalFile
        require(destination.toPath().startsWith(base.toPath())) { "Invalid destination" }

        val input = context.contentResolver.openInputStream(zipUri)
            ?: error("Cannot open selected ZIP file")

        input.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val output = File(destination, entry.name).canonicalFile
                    require(output.toPath().startsWith(destination.toPath())) {
                        "ZIP entry escapes destination: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return destination
    }
}
