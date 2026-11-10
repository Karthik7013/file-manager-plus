package com.filemanagerplus.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.filemanagerplus.data.FileCategory
import com.filemanagerplus.data.StorageItem
import com.filemanagerplus.data.StorageSort
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fileIconAndTint(item: StorageItem): Pair<ImageVector, Color> {
    if (item.isDirectory) return Icons.Default.Folder to Color(0xFFF9A825)
    val name = item.name.lowercase()
    val mime = item.mimeType.orEmpty().lowercase()
    return when {
        mime.startsWith("image/") || name.hasExt("jpg","jpeg","png","gif","webp","bmp","heic","heif") ->
            Icons.Default.Image to Color(0xFF43A047)
        mime.startsWith("video/") || name.hasExt("mp4","mkv","webm","avi","mov","3gp") ->
            Icons.Default.Movie to Color(0xFFE53935)
        mime.startsWith("audio/") || name.hasExt("mp3","m4a","aac","flac","wav","ogg","opus") ->
            Icons.Default.AudioFile to Color(0xFF8E24AA)
        name.endsWith(".pdf") -> Icons.Default.PictureAsPdf to Color(0xFFD32F2F)
        name.hasExt("zip","rar","7z","tar","gz") -> Icons.Default.FolderZip to Color(0xFF6D4C41)
        name.endsWith(".apk") -> Icons.Default.Android to Color(0xFF43A047)
        name.hasExt("doc","docx","xls","xlsx","ppt","pptx","txt","rtf","csv","md") || mime.startsWith("text/") ->
            Icons.Default.Description to Color(0xFF1E88E5)
        else -> Icons.Default.InsertDriveFile to Color(0xFF607D8B)
    }
}

private fun String.hasExt(vararg exts: String) = exts.any { endsWith(".$it") }

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
    return if (i == 0) "${bytes} B" else "${DecimalFormat("#,##0.#").format(value)} ${units[i]}"
}

fun formatShortDate(millis: Long): String =
    if (millis <= 0) "—" else SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))

fun formatFullDate(millis: Long): String =
    if (millis <= 0) "Unknown" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun StorageSort.label() = when (this) {
    StorageSort.NAME -> "Name"; StorageSort.DATE -> "Date"; StorageSort.SIZE -> "Size"; StorageSort.TYPE -> "Type"
}

fun FileCategory.icon(): ImageVector = when (this) {
    FileCategory.IMAGES -> Icons.Default.Image
    FileCategory.VIDEOS -> Icons.Default.Movie
    FileCategory.AUDIO -> Icons.Default.MusicNote
    FileCategory.DOCUMENTS -> Icons.Default.Description
    FileCategory.APKS -> Icons.Default.Android
}

fun FileCategory.color(): Color = when (this) {
    FileCategory.IMAGES -> Color(0xFF43A047)
    FileCategory.VIDEOS -> Color(0xFFE53935)
    FileCategory.AUDIO -> Color(0xFF8E24AA)
    FileCategory.DOCUMENTS -> Color(0xFF1E88E5)
    FileCategory.APKS -> Color(0xFF00897B)
}

fun isPlayable(item: StorageItem): Boolean {
    val mime = item.mimeType.orEmpty()
    return mime.startsWith("audio/") || mime.startsWith("video/") ||
        item.name.substringAfterLast('.', "").lowercase() in
        setOf("mp3","m4a","aac","flac","wav","ogg","opus","mp4","mkv","webm","avi","mov","3gp")
}
