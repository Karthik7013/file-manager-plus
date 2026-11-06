package com.filemanagerplus.data

/** Shared, platform-independent validation for names created or assigned through SAF. */
internal fun validateDisplayName(name: String): String {
    val cleaned = name.trim()
    require(cleaned.isNotEmpty() && cleaned != "." && cleaned != ".." &&
        !cleaned.contains('/') && !cleaned.contains('\\')) {
        "Enter a valid file or folder name"
    }
    return cleaned
}
