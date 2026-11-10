package com.filemanagerplus.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.filemanagerplus.data.StorageItem
import java.io.File

internal fun openNonMediaFile(context: Context, item: StorageItem) {
    val file = File(item.path)
    val uri: Uri = try {
        FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    } catch (_: Exception) {
        Uri.fromFile(file)
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        })
    }
}
