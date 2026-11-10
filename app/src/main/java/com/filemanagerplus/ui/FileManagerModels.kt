package com.filemanagerplus.ui

import com.filemanagerplus.data.StorageItem

internal const val FILE_PROVIDER_AUTHORITY = "com.filemanagerplus.fileprovider"

internal enum class Screen { HOME, BROWSER, CATEGORY, SETTINGS, PLAYER }
internal enum class FileAction { DETAILS, RENAME, COPY, MOVE, DELETE }

internal data class PendingTransfer(val item: StorageItem, val move: Boolean)
