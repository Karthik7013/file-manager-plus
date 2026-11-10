package com.filemanagerplus.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filemanagerplus.data.FileCategory
import com.filemanagerplus.data.StorageItem
import com.filemanagerplus.data.StorageRepository
import com.filemanagerplus.data.StorageSort
import com.filemanagerplus.data.StorageUsage
import com.filemanagerplus.media.MediaPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserScreen(
    repository: StorageRepository, scope: kotlinx.coroutines.CoroutineScope, playback: MediaPlaybackController,
    currentPath: String, rootPath: String?,
    onNavigate: (String) -> Unit, onOpenCategory: (FileCategory) -> Unit, onPlay: (StorageItem) -> Unit,
    showHidden: Boolean, sort: StorageSort, gridView: Boolean, onToggleGrid: () -> Unit, onBack: () -> Unit
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameItem by remember { mutableStateOf<StorageItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteItem by remember { mutableStateOf<StorageItem?>(null) }
    var detailsItem by remember { mutableStateOf<StorageItem?>(null) }
    var pendingTransfer by remember { mutableStateOf<PendingTransfer?>(null) }
    var showUsage by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var usageLoading by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true; error = null
            val result = withContext(Dispatchers.IO) { runCatching { repository.list(currentPath, searchQuery, showHidden, sort) } }
            result.onSuccess { items = it }.onFailure { error = it.message; items = emptyList() }
            loading = false
        }
    }
    LaunchedEffect(currentPath, searchQuery, showHidden, sort) { refresh() }

    fun openItem(item: StorageItem) {
        if (item.isDirectory) onNavigate(item.path)
        else if (isPlayable(item)) onPlay(item)
        else openNonMediaFile(context, item)
    }

    val destPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val req = pendingTransfer
        if (uri != null && req != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (req.item.isDirectory) {
                            if (uri.toString().contains(req.item.path)) error("Cannot copy/move a folder into itself")
                            if (repository.destinationExistsUri(uri, context.contentResolver, req.item.name)) error("Already exists in destination")
                            repository.copyDirectoryToUri(req.item.path, uri, context.contentResolver)
                            if (req.move) repository.delete(req.item.path)
                        } else {
                            if (repository.destinationExistsUri(uri, context.contentResolver, req.item.name)) error("Already exists in destination")
                            repository.copyToUri(req.item.path, uri, context.contentResolver)
                            if (req.move) repository.delete(req.item.path)
                        }
                    }
                }
                pendingTransfer = null; refresh()
            }
        } else pendingTransfer = null
    }

    fun handleAction(item: StorageItem, action: FileAction) {
        when (action) {
            FileAction.DETAILS -> detailsItem = item
            FileAction.RENAME -> { renameItem = item; renameText = item.name }
            FileAction.COPY -> { pendingTransfer = PendingTransfer(item, false); destPicker.launch(null) }
            FileAction.MOVE -> { pendingTransfer = PendingTransfer(item, true); destPicker.launch(null) }
            FileAction.DELETE -> deleteItem = item
        }
    }

    val folderName = File(currentPath).name.ifBlank { "Storage" }
    val canGoUp = rootPath != null && currentPath != rootPath

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(if (loading) "Loading…" else "${items.size} items", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = { if (canGoUp) onNavigate(File(currentPath).parent ?: rootPath!!) else onBack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Default.Search, "Search") }
                        IconButton(onClick = onToggleGrid) { Icon(if (gridView) Icons.Default.ViewList else Icons.Default.GridView, "View") }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("New folder") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { showMenu = false; showCreateFolder = true })
                                DropdownMenuItem(text = { Text("Storage usage") }, leadingIcon = { Icon(Icons.Default.PieChart, null) }, onClick = { showMenu = false; showUsage = true; usage = null; usageLoading = true; scope.launch { usage = withContext(Dispatchers.IO) { runCatching { repository.calculateUsage(currentPath) }.getOrNull() }; usageLoading = false } })
                                DropdownMenuItem(text = { Text("Refresh") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { showMenu = false; refresh() })
                            }
                        }
                    }
                )
                if (showSearch) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                        singleLine = true)
                }
            }
        },
        floatingActionButton = { FloatingActionButton(onClick = { showCreateFolder = true }) { Icon(Icons.Default.CreateNewFolder, "New folder") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BreadcrumbBar(currentPath, rootPath, onNavigate)
            SortBarRow(sort)
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(error!!, color = MaterialTheme.colorScheme.error) }
                items.isEmpty() -> EmptyState(if (searchQuery.isNotEmpty()) "No results" else "This folder is empty")
                gridView -> LazyVerticalGrid(columns = GridCells.Adaptive(110.dp), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.path }) { item -> FileGridItem(item, { openItem(item) }, { handleAction(item, it) }) }
                }
                else -> LazyColumn { items(items, key = { it.path }) { item ->
                    FileListRow(item, { openItem(item) }, { handleAction(item, it) })
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }; item { Spacer(Modifier.height(80.dp)) } }
            }
        }
    }

    if (showCreateFolder) NameDialog("New folder", newFolderName, { newFolderName = it }, "Create", { showCreateFolder = false; newFolderName = "" }) {
        scope.launch { withContext(Dispatchers.IO) { runCatching { repository.createFolder(currentPath, newFolderName) } }; showCreateFolder = false; newFolderName = ""; refresh() }
    }
    renameItem?.let { item -> NameDialog("Rename", renameText, { renameText = it }, "Save", { renameItem = null }) {
        scope.launch { withContext(Dispatchers.IO) { runCatching { repository.rename(item.path, renameText) } }; renameItem = null; refresh() }
    } }
    deleteItem?.let { item -> AlertDialog(onDismissRequest = { deleteItem = null }, title = { Text("Delete ${item.name}?") },
        text = { Text("This will permanently delete this ${if (item.isDirectory) "folder" else "file"}.") },
        confirmButton = { TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { runCatching { repository.delete(item.path) } }; deleteItem = null; refresh() } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Cancel") } }) }
    detailsItem?.let { item -> DetailsDialog(item) { detailsItem = null } }
    if (showUsage) UsageDialog(usage, usageLoading) { showUsage = false }
}

