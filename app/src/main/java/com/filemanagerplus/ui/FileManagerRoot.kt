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
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date

private const val FILE_PROVIDER_AUTHORITY = "com.filemanagerplus.fileprovider"

private enum class FileAction { DETAILS, RENAME, COPY, MOVE, DELETE }

private data class PendingTransfer(
    val item: StorageItem,
    val sourceParent: String,
    val destUri: Uri?,
    val move: Boolean
)

@Composable
fun FileManagerRoot(isTv: Boolean, onToggleTheme: () -> Unit, isDark: Boolean) {
    val context = LocalContext.current
    val repository = remember { StorageRepository(context) }
    val playback = remember { MediaPlaybackController(context) }
    val scope = rememberCoroutineScope()

    var rootPath by remember { mutableStateOf<String?>(null) }
    var currentPath by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    var title by remember { mutableStateOf("File Manager +") }
    var message by remember { mutableStateOf("Select a storage location to start browsing.") }
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
        val path = currentPath ?: return
        runCatching { repository.list(path, searchQuery, showHidden, sort) }
            .onSuccess { items = it }
            .onFailure { message = it.message ?: "Could not read this folder" }
    }

    fun selectRoot(root: StorageItem) {
        rootPath = root.path
        currentPath = root.path
        title = repository.displayName(root.path)
        message = ""
    }

    fun goUp() {
        val current = currentPath ?: return
        val parent = File(current).parentFile
        if (parent == null) return
        val parentPath = parent.absolutePath
        if (rootPath != null && !parentPath.startsWith(rootPath!!)) return
        currentPath = parentPath
        title = repository.displayName(parentPath)
        message = ""
        refreshListing()
    }

    fun handlePendingTransfer(destUri: Uri) {
        val request = pendingTransfer
        if (request != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (request.item.isDirectory) {
                            // Check if dest URI path contains source path (self/descendant move)
                            if (destUri.toString().contains(request.item.path)) {
                                error("A folder cannot be moved into itself or a descendant")
                            }
                            if (repository.destinationExistsUri(destUri, context.contentResolver, request.item.name)) {
                                error("'${request.item.name}' already exists in the destination")
                            }
                            repository.copyDirectoryToUri(request.item.path, destUri, context.contentResolver)
                            if (request.move) {
                                repository.delete(request.item.path)
                            }
                        } else {
                            if (repository.destinationExistsUri(destUri, context.contentResolver, request.item.name)) {
                                error("'${request.item.name}' already exists in the destination")
                            }
                            repository.copyToUri(request.item.path, destUri, context.contentResolver)
                            if (request.move) {
                                repository.delete(request.item.path)
                            }
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
        }
    }

    fun openNonMediaFile(item: StorageItem) {
        val file = File(item.path)
        val uri: Uri = try {
            FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            context.startActivity(intent)
        }.onFailure { message = "No app can open ${item.name}" }
    }

    LaunchedEffect(currentPath, searchQuery, showHidden, sort) {
        currentPath?.let { path ->
            runCatching { repository.list(path, searchQuery, showHidden, sort) }
                .onSuccess { items = it }
                .onFailure { message = it.message ?: "Could not read this folder" }
        }
    }

    val destinationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { destinationUri ->
        if (destinationUri != null) {
            handlePendingTransfer(destinationUri)
        } else {
            pendingTransfer = null
        }
    }

    DisposableEffect(playback) { onDispose { playback.release() } }

    fun openItem(item: StorageItem) {
        if (item.isDirectory) {
            currentPath = item.path
            title = item.name
            message = ""
            refreshListing()
        } else if (isPlayable(item)) {
            runCatching { playback.play(Uri.fromFile(File(item.path))) }
                .onSuccess { message = "Playing: ${item.name}" }
                .onFailure { message = it.message ?: "Could not play this file" }
        } else {
            openNonMediaFile(item)
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
                val parent = currentPath
                if (parent == null) {
                    message = "Select a folder first"
                } else {
                    pendingTransfer = PendingTransfer(item, parent, null, action == FileAction.MOVE)
                    destinationPicker.launch(null)
                }
            }
            FileAction.DELETE -> deleteItem = item
        }
    }

    fun goBack() {
        val root = rootPath
        if (root != null && currentPath != null && currentPath != root) {
            currentPath = root
            title = repository.displayName(root)
            message = ""
            refreshListing()
        }
    }

    fun calculateStorageUsage() {
        val uri = rootPath ?: currentPath ?: return
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

    val showRoots = currentPath == null

    if (showRoots) {
        RootsScreen(
            title = title,
            onRootSelected = ::selectRoot,
            onToggleTheme = onToggleTheme,
            isDark = isDark,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            showHidden = showHidden,
            onShowHiddenChange = { showHidden = it },
            sort = sort,
            onSortChange = { sort = it },
            onCreateFolder = { showCreateFolder = true },
            onStorageInfo = ::calculateStorageUsage,
            onRefresh = { }
        )
    } else {
        if (isTv) {
            TvFileManagerScreen(
                title = title,
                items = items,
                message = message,
                onChooseRoot = { },
                onGoUp = ::goUp,
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
                onChooseRoot = { },
                onGoUp = ::goUp,
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
    }

    if (showCreateFolder && currentPath != null) {
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
                    val parent = currentPath
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
                    runCatching { repository.rename(item.path, renameText) }
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
            text = { Text("This permanently deletes the selected item.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { repository.delete(item.path) }
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
                    DetailLine("Size", if (item.isDirectory) "Calculated in Storage info" else if (item.sizeKnown) formatSize(item.size) else "Not reported")
                    DetailLine("Modified", item.lastModified.takeIf { it > 0L }?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Not reported")
                    DetailLine("Location", item.path)
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
                        Text("This is a recursive total for the selected folder.", style = MaterialTheme.typography.bodySmall)
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
    onChooseRoot: () -> Unit, onGoUp: () -> Unit, onOpen: (StorageItem) -> Unit,
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
                    IconButton(onClick = onGoUp) { Icon(Icons.Default.ArrowUpward, "Go up") }
                    IconButton(onClick = onStorageInfo) { Icon(Icons.Default.Storage, "Storage usage") }
                    IconButton(onClick = onToggleTheme) {
                        Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreateFolder, icon = {
                Icon(Icons.Default.CreateNewFolder, null)
            }, text = { Text("New folder") })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            FileListTools(searchQuery, onSearchChange, showHidden, onShowHiddenChange, sort, onSortChange, onCreateFolder)
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.path }) { item ->
                    FileRow(item, onClick = { onOpen(item) }, onAction = { action -> onAction(item, action) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvFileManagerScreen(
    title: String, items: List<StorageItem>, message: String,
    onChooseRoot: () -> Unit, onGoUp: () -> Unit, onOpen: (StorageItem) -> Unit,
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
            Button(onClick = onGoUp, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ArrowUpward, null); Spacer(Modifier.width(8.dp)); Text("Go up")
            }
            Button(onClick = onStorageInfo, modifier = Modifier.fillMaxWidth()) { Text("Storage usage") }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            Button(onClick = onToggleTheme, modifier = Modifier.fillMaxWidth()) {
                Text(if (isDark) "Light theme" else "Dark theme")
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            FileListTools(searchQuery, onSearchChange, showHidden, onShowHiddenChange, sort, onSortChange, onCreateFolder)
            if (message.isNotBlank()) Text(message, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.path }) { item ->
                    TvFileRow(item, onClick = { onOpen(item) }, onAction = { action -> onAction(item, action) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootsScreen(
    title: String,
    onRootSelected: (StorageItem) -> Unit,
    onToggleTheme: () -> Unit,
    isDark: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    sort: StorageSort,
    onSortChange: (StorageSort) -> Unit,
    onCreateFolder: () -> Unit,
    onStorageInfo: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { StorageRepository(context) }
    val roots by remember { mutableStateOf(repository.listRoots()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Select storage location", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(roots, key = { it.path }) { root ->
                    Card(
                        modifier = Modifier.clickable { onRootSelected(root) },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Storage, null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(root.name, style = MaterialTheme.typography.bodyLarge)
                                Text(root.path, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ArrowForward, null)
                        }
                    }
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
