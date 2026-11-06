package com.filemanagerplus.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filemanagerplus.data.StorageItem
import com.filemanagerplus.data.StorageRepository
import com.filemanagerplus.data.StorageSort
import com.filemanagerplus.data.StorageUsage
import com.filemanagerplus.media.MediaPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date

private enum class FileAction { DETAILS, RENAME, COPY, MOVE, DELETE }

private data class PendingTransfer(
    val item: StorageItem,
    val sourceParent: Uri,
    val move: Boolean
)

@Composable
fun FileManagerRoot(isTv: Boolean, onToggleTheme: () -> Unit, isDark: Boolean) {
    val context = LocalContext.current
    val repository = remember { StorageRepository(context) }
    val playback = remember { MediaPlaybackController(context) }
    val scope = rememberCoroutineScope()

    var rootUri by remember { mutableStateOf<Uri?>(null) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var items by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    var title by remember { mutableStateOf("File Manager +") }
    var message by remember { mutableStateOf("Choose a folder to start browsing.") }
    var searchQuery by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(StorageSort.NAME) }

    var showCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameItem by remember { mutableStateOf<StorageItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteItem by remember { mutableStateOf<StorageItem?>(null) }
    var detailsItem by remember { mutableStateOf<StorageItem?>(null) }
    var pendingTransfer by remember { mutableStateOf<PendingTransfer?>(null) }
    var storageUsage by remember { mutableStateOf<StorageUsage?>(null) }
    var storageError by remember { mutableStateOf<String?>(null) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var storageLoading by remember { mutableStateOf(false) }

    fun refreshListing() {
        val uri = currentUri ?: return
        runCatching { repository.list(uri, searchQuery, showHidden, sort) }
            .onSuccess { items = it }
            .onFailure { message = it.message ?: "Could not read this folder" }
    }

    fun rememberGrant(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    LaunchedEffect(currentUri, searchQuery, showHidden, sort) {
        currentUri?.let { uri ->
            runCatching { repository.list(uri, searchQuery, showHidden, sort) }
                .onSuccess { items = it }
                .onFailure { message = it.message ?: "Could not read this folder" }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            rememberGrant(uri)
            rootUri = uri
            currentUri = uri
            title = repository.displayName(uri)
            message = ""
        }
    }

    val destinationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { destinationUri ->
        val request = pendingTransfer
        if (destinationUri != null && request != null) {
            rememberGrant(destinationUri)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (request.move && repository.sameDocument(request.sourceParent, destinationUri)) {
                            error("Choose a different folder to move this item")
                        }
                        if (request.item.isDirectory && repository.sameDocument(request.item.uri, destinationUri)) {
                            error("A folder cannot be copied or moved into itself")
                        }
                        if (request.move) {
                            repository.move(request.item.uri, request.sourceParent, destinationUri)
                        } else {
                            repository.copy(request.item.uri, destinationUri)
                        }
                    }
                }
                result.onSuccess {
                    message = if (request.move) "Moved ${request.item.name}" else "Copied ${request.item.name}"
                    refreshListing()
                }.onFailure {
                    message = it.message ?: "File operation failed"
                }
                pendingTransfer = null
            }
        } else {
            pendingTransfer = null
        }
    }

    DisposableEffect(playback) { onDispose { playback.release() } }

    fun openItem(item: StorageItem) {
        if (item.isDirectory) {
            currentUri = item.uri
            title = item.name
            message = ""
        } else if (isPlayable(item)) {
            runCatching { playback.play(item.uri) }
                .onSuccess { message = "Playing: ${item.name}" }
                .onFailure { message = it.message ?: "Could not play this file" }
        } else {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.uri, item.mimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }.onFailure { message = "No app can open ${item.name}" }
        }
    }

    fun handleAction(item: StorageItem, action: FileAction) {
        when (action) {
            FileAction.DETAILS -> detailsItem = item
            FileAction.RENAME -> {
                renameItem = item
                renameText = item.name
            }
            FileAction.COPY, FileAction.MOVE -> {
                val parent = currentUri
                if (parent == null) {
                    message = "Choose a folder first"
                } else {
                    pendingTransfer = PendingTransfer(item, parent, action == FileAction.MOVE)
                    destinationPicker.launch(null)
                }
            }
            FileAction.DELETE -> deleteItem = item
        }
    }

    fun goBack() {
        val root = rootUri
        if (root != null && currentUri != null && currentUri != root) {
            // The first MVP navigation model returns to the granted root.
            currentUri = root
            title = repository.displayName(root)
            message = ""
        }
    }

    fun calculateStorageUsage() {
        val uri = rootUri ?: currentUri ?: return
        storageLoading = true
        storageUsage = null
        storageError = null
        showStorageDialog = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.calculateUsage(uri) } }
            result.onSuccess { storageUsage = it }
                .onFailure {
                    storageError = it.message ?: "Could not calculate storage usage"
                    message = storageError ?: "Could not calculate storage usage"
                }
            storageLoading = false
        }
    }

    val sharedActions: (StorageItem, FileAction) -> Unit = { item, action -> handleAction(item, action) }

    if (isTv) {
        TvFileManagerScreen(
            title = title,
            items = items,
            message = message,
            onChooseFolder = { folderPicker.launch(null) },
            onOpen = ::openItem,
            onAction = sharedActions,
            onBack = ::goBack,
            onToggleTheme = onToggleTheme,
            isDark = isDark,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            showHidden = showHidden,
            onShowHiddenChange = { showHidden = it },
            sort = sort,
            onSortChange = { sort = it },
            onCreateFolder = { showCreateFolder = true },
            onStorageInfo = ::calculateStorageUsage
        )
    } else {
        MobileFileManagerScreen(
            title = title,
            items = items,
            message = message,
            onChooseFolder = { folderPicker.launch(null) },
            onOpen = ::openItem,
            onAction = sharedActions,
            onBack = ::goBack,
            onToggleTheme = onToggleTheme,
            isDark = isDark,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            showHidden = showHidden,
            onShowHiddenChange = { showHidden = it },
            sort = sort,
            onSortChange = { sort = it },
            onCreateFolder = { showCreateFolder = true },
            onStorageInfo = ::calculateStorageUsage
        )
    }

    if (showCreateFolder && currentUri != null) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false; newFolderName = "" },
            title = { Text("Create folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = newFolderName.isNotBlank(), onClick = {
                    val parent = currentUri
                    if (parent != null) {
                        runCatching { repository.createFolder(parent, newFolderName) }
                            .onSuccess { message = "Folder created" }
                            .onFailure { message = it.message ?: "Could not create folder" }
                        refreshListing()
                    }
                    showCreateFolder = false
                    newFolderName = ""
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolder = false; newFolderName = "" }) { Text("Cancel") }
            }
        )
    }

    renameItem?.let { item ->
        AlertDialog(
            onDismissRequest = { renameItem = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = {
                    runCatching { repository.rename(item.uri, renameText) }
                        .onSuccess { message = "Renamed successfully" }
                        .onFailure { message = it.message ?: "Could not rename item" }
                    renameItem = null
                    refreshListing()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameItem = null }) { Text("Cancel") } }
        )
    }

    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("This permanently deletes the selected item if the storage provider allows it.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { repository.delete(item.uri) }
                        .onSuccess { message = "Deleted ${item.name}" }
                        .onFailure { message = it.message ?: "Could not delete item" }
                    deleteItem = null
                    refreshListing()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Cancel") } }
        )
    }

    detailsItem?.let { item ->
        AlertDialog(
            onDismissRequest = { detailsItem = null },
            title = { Text("File details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailLine("Name", item.name)
                    DetailLine("Type", if (item.isDirectory) "Folder" else item.mimeType ?: "Unknown")
                    DetailLine("Size", if (item.isDirectory) "Calculated in Storage info" else if (item.sizeKnown) formatSize(item.size) else "Not reported by provider")
                    DetailLine("Modified", item.lastModified.takeIf { it > 0L }?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Not reported")
                    DetailLine("Location", item.uri.toString())
                }
            },
            confirmButton = { TextButton(onClick = { detailsItem = null }) { Text("Close") } }
        )
    }

    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Storage usage") },
            text = {
                when {
                    storageLoading -> CircularProgressIndicator()
                    storageError != null -> Text(storageError!!)
                    storageUsage != null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailLine("Known file size total", formatSize(storageUsage!!.totalBytes))
                        DetailLine("Files", storageUsage!!.fileCount.toString())
                        DetailLine("Folders", storageUsage!!.folderCount.toString())
                        if (storageUsage!!.unknownSizeCount > 0) {
                            DetailLine("Files without reported size", storageUsage!!.unknownSizeCount.toString())
                        }
                        Text("This is a recursive total for the selected SAF folder, not the device's full free-space figure.", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Text("Storage usage is unavailable.")
                }
            },
            confirmButton = { TextButton(onClick = { showStorageDialog = false }) { Text("Close") } }
        )
    }
}

private fun isPlayable(item: StorageItem): Boolean {
    val mime = item.mimeType.orEmpty()
    return mime.startsWith("audio/") || mime.startsWith("video/") ||
        item.name.substringAfterLast('.', "").lowercase() in
        setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "mp4", "mkv", "webm", "avi")
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileFileManagerScreen(
    title: String, items: List<StorageItem>, message: String,
    onChooseFolder: () -> Unit, onOpen: (StorageItem) -> Unit,
    onAction: (StorageItem, FileAction) -> Unit, onBack: () -> Unit,
    onToggleTheme: () -> Unit, isDark: Boolean,
    searchQuery: String, onSearchChange: (String) -> Unit,
    showHidden: Boolean, onShowHiddenChange: (Boolean) -> Unit,
    sort: StorageSort, onSortChange: (StorageSort) -> Unit,
    onCreateFolder: () -> Unit, onStorageInfo: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onStorageInfo) { Icon(Icons.Default.Storage, "Storage usage") }
                    IconButton(onClick = onToggleTheme) {
                        Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onChooseFolder, icon = {
                Icon(Icons.Default.FolderOpen, null)
            }, text = { Text("Choose folder") })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            FileListTools(searchQuery, onSearchChange, showHidden, onShowHiddenChange, sort, onSortChange, onCreateFolder)
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.uri.toString() }) { item ->
                    FileRow(item, onClick = { onOpen(item) }, onAction = { action -> onAction(item, action) })
                }
            }
        }
    }
}

@Composable
private fun TvFileManagerScreen(
    title: String, items: List<StorageItem>, message: String,
    onChooseFolder: () -> Unit, onOpen: (StorageItem) -> Unit,
    onAction: (StorageItem, FileAction) -> Unit, onBack: () -> Unit,
    onToggleTheme: () -> Unit, isDark: Boolean,
    searchQuery: String, onSearchChange: (String) -> Unit,
    showHidden: Boolean, onShowHiddenChange: (Boolean) -> Unit,
    sort: StorageSort, onSortChange: (StorageSort) -> Unit,
    onCreateFolder: () -> Unit, onStorageInfo: () -> Unit
) {
    Row(Modifier.fillMaxSize().padding(36.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("FILE MANAGER +", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose storage")
            }
            OutlinedButton(onClick = onStorageInfo, modifier = Modifier.fillMaxWidth()) { Text("Storage usage") }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            OutlinedButton(onClick = onToggleTheme, modifier = Modifier.fillMaxWidth()) {
                Text(if (isDark) "Light theme" else "Dark theme")
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            FileListTools(searchQuery, onSearchChange, showHidden, onShowHiddenChange, sort, onSortChange, onCreateFolder)
            if (message.isNotBlank()) Text(message, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.uri.toString() }) { item ->
                    TvFileRow(item, onClick = { onOpen(item) }, onAction = { action -> onAction(item, action) })
                }
            }
        }
    }
}

@Composable
private fun FileListTools(
    query: String,
    onQueryChange: (String) -> Unit,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    sort: StorageSort,
    onSortChange: (StorageSort) -> Unit,
    onCreateFolder: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Search this folder") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Button(onClick = onCreateFolder) { Text("New folder") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Icon(Icons.Default.Sort, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Sort: ${sort.name.lowercase().replaceFirstChar { it.uppercase() }}")
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    StorageSort.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = { onSortChange(option); sortMenuExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Hidden", style = MaterialTheme.typography.bodySmall)
            Switch(checked = showHidden, onCheckedChange = onShowHiddenChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileRow(item: StorageItem, onClick: () -> Unit, onAction: (FileAction) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1) },
        supportingContent = { if (!item.isDirectory) Text(if (item.sizeKnown) formatSize(item.size) else "Size unavailable") },
        leadingContent = {
            Icon(if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "File actions") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    FileAction.values().forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label()) },
                            onClick = { menuExpanded = false; onAction(action) }
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun TvFileRow(item: StorageItem, onClick: () -> Unit, onAction: (FileAction) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 18.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                if (!item.isDirectory) Text(if (item.sizeKnown) formatSize(item.size) else "Size unavailable", style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "File actions") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    FileAction.values().forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label()) },
                            onClick = { menuExpanded = false; onAction(action) }
                        )
                    }
                }
            }
        }
    }
}

private fun FileAction.label(): String = when (this) {
    FileAction.DETAILS -> "Details"
    FileAction.RENAME -> "Rename"
    FileAction.COPY -> "Copy to…"
    FileAction.MOVE -> "Move to…"
    FileAction.DELETE -> "Delete"
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do { value /= 1024; index++ } while (value >= 1024 && index < units.lastIndex)
    return "${DecimalFormat("#,##0.##").format(value)} ${units[index]}"
}
