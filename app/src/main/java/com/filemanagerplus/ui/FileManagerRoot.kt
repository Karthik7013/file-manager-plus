package com.filemanagerplus.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            .onFailure { /* keep previous items, show error via snackbar-like message not stored */ }
    }

    fun selectRoot(root: StorageItem) {
        rootPath = root.path
        currentPath = root.path
    }

    fun navigateTo(path: String) {
        currentPath = path
    }

    fun goUp() {
        val current = currentPath ?: return
        val parent = File(current).parentFile ?: return
        val parentPath = parent.absolutePath
        if (rootPath != null && !parentPath.startsWith(rootPath!!)) return
        currentPath = parentPath
    }

    fun handlePendingTransfer(destUri: Uri) {
        val request = pendingTransfer ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (request.item.isDirectory) {
                        if (destUri.toString().contains(request.item.path)) {
                            error("A folder cannot be moved into itself or a descendant")
                        }
                        if (repository.destinationExistsUri(destUri, context.contentResolver, request.item.name)) {
                            error("'${request.item.name}' already exists in the destination")
                        }
                        repository.copyDirectoryToUri(request.item.path, destUri, context.contentResolver)
                        if (request.move) repository.delete(request.item.path)
                    } else {
                        if (repository.destinationExistsUri(destUri, context.contentResolver, request.item.name)) {
                            error("'${request.item.name}' already exists in the destination")
                        }
                        repository.copyToUri(request.item.path, destUri, context.contentResolver)
                        if (request.move) repository.delete(request.item.path)
                    }
                }
            }
            result.onFailure { /* could show snackbar; kept simple */ }
            pendingTransfer = null
            refreshListing()
        }
    }

    fun openNonMediaFile(item: StorageItem) {
        val file = File(item.path)
        val uri: Uri = try {
            FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            context.startActivity(intent)
        }
    }

    LaunchedEffect(currentPath, searchQuery, showHidden, sort) {
        currentPath?.let { path ->
            runCatching { repository.list(path, searchQuery, showHidden, sort) }
                .onSuccess { items = it }
                .onFailure { items = emptyList() }
        }
    }

    val destinationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { destinationUri ->
        if (destinationUri != null) handlePendingTransfer(destinationUri) else pendingTransfer = null
    }

    DisposableEffect(playback) { onDispose { playback.release() } }

    fun openItem(item: StorageItem) {
        if (item.isDirectory) {
            currentPath = item.path
        } else if (isPlayable(item)) {
            runCatching { playback.play(Uri.fromFile(File(item.path))) }
        } else {
            openNonMediaFile(item)
        }
    }

    fun handleAction(item: StorageItem, action: FileAction) {
        when (action) {
            FileAction.DETAILS -> detailsItem = item
            FileAction.RENAME -> { renameItem = item; renameText = item.name }
            FileAction.COPY, FileAction.MOVE -> {
                val parent = currentPath ?: return
                pendingTransfer = PendingTransfer(item, parent, null, action == FileAction.MOVE)
                destinationPicker.launch(null)
            }
            FileAction.DELETE -> deleteItem = item
        }
    }

    fun goBackToRoot() {
        val root = rootPath ?: return
        if (currentPath != root) currentPath = root
    }

    fun calculateStorageUsage() {
        val uri = rootPath ?: currentPath ?: return
        storageLoading = true
        storageUsage = null
        storageError = null
        showStorageDialog = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.calculateUsage(uri) } }
            result.onSuccess { storageUsage = it }.onFailure { storageError = it.message ?: "Could not calculate storage usage" }
            storageLoading = false
        }
    }

    val showRoots = currentPath == null

    if (showRoots) {
        RootsScreen(
            onRootSelected = ::selectRoot,
            onToggleTheme = onToggleTheme,
            isDark = isDark
        )
    } else if (isTv) {
        TvFileManagerScreen(
            currentPath = currentPath!!,
            rootPath = rootPath,
            items = items,
            onNavigateTo = ::navigateTo,
            onGoUp = ::goUp,
            onOpen = ::openItem,
            onAction = ::handleAction,
            onBack = ::goBackToRoot,
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
            currentPath = currentPath!!,
            rootPath = rootPath,
            items = items,
            onNavigateTo = ::navigateTo,
            onGoUp = ::goUp,
            onOpen = ::openItem,
            onAction = ::handleAction,
            onBack = ::goBackToRoot,
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

    if (showCreateFolder && currentPath != null) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false; newFolderName = "" },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName, onValueChange = { newFolderName = it },
                    label = { Text("Folder name") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = newFolderName.isNotBlank(), onClick = {
                    currentPath?.let { parent ->
                        runCatching { repository.createFolder(parent, newFolderName) }
                        refreshListing()
                    }
                    showCreateFolder = false; newFolderName = ""
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolder = false; newFolderName = "" }) { Text("Cancel") } }
        )
    }

    renameItem?.let { item ->
        AlertDialog(
            onDismissRequest = { renameItem = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = {
                    runCatching { repository.rename(item.path, renameText) }
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
            text = { Text("This will permanently delete this ${if (item.isDirectory) "folder and its contents" else "file"}.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { repository.delete(item.path) }
                    deleteItem = null
                    refreshListing()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Cancel") } }
        )
    }

    detailsItem?.let { item ->
        AlertDialog(
            onDismissRequest = { detailsItem = null },
            title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLine("Type", if (item.isDirectory) "Folder" else item.mimeType ?: "File")
                    DetailLine("Size", if (item.isDirectory) "—" else if (item.sizeKnown) formatSize(item.size) else "Unknown")
                    DetailLine("Modified", item.lastModified.takeIf { it > 0L }?.let { formatFullDate(it) } ?: "Unknown")
                    DetailLine("Path", item.path)
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
                    storageLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    storageError != null -> Text(storageError!!)
                    storageUsage != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailLine("Total size", formatSize(storageUsage!!.totalBytes))
                        DetailLine("Files", storageUsage!!.fileCount.toString())
                        DetailLine("Folders", storageUsage!!.folderCount.toString())
                        if (storageUsage!!.unknownSizeCount > 0) {
                            DetailLine("Unknown size", storageUsage!!.unknownSizeCount.toString())
                        }
                    }
                    else -> Text("Unavailable")
                }
            },
            confirmButton = { TextButton(onClick = { showStorageDialog = false }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileFileManagerScreen(
    currentPath: String, rootPath: String?, items: List<StorageItem>,
    onNavigateTo: (String) -> Unit, onGoUp: () -> Unit, onOpen: (StorageItem) -> Unit,
    onAction: (StorageItem, FileAction) -> Unit, onBack: () -> Unit,
    onToggleTheme: () -> Unit, isDark: Boolean,
    searchQuery: String, onSearchChange: (String) -> Unit,
    showHidden: Boolean, onShowHiddenChange: (Boolean) -> Unit,
    sort: StorageSort, onSortChange: (StorageSort) -> Unit,
    onCreateFolder: () -> Unit, onStorageInfo: () -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val folderName = File(currentPath).name.ifBlank { currentPath }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("${items.size} items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (currentPath != rootPath) onGoUp() else onBack() }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Default.Search, "Search") }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("New folder") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                    onClick = { showMenu = false; onCreateFolder() })
                                DropdownMenuItem(text = { Text("Storage usage") }, leadingIcon = { Icon(Icons.Default.PieChart, null) },
                                    onClick = { showMenu = false; onStorageInfo() })
                                DropdownMenuItem(text = { Text(if (isDark) "Light theme" else "Dark theme") },
                                    leadingIcon = { Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, null) },
                                    onClick = { showMenu = false; onToggleTheme() })
                                DropdownMenuItem(text = { Text(if (showHidden) "Hide hidden files" else "Show hidden files") },
                                    leadingIcon = { Icon(Icons.Default.Visibility, null) },
                                    onClick = { showMenu = false; onShowHiddenChange(!showHidden) })
                            }
                        }
                    }
                )
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Search in this folder") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchChange("") }) { Icon(Icons.Default.Close, null) } },
                        singleLine = true
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateFolder, containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.CreateNewFolder, "New folder")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BreadcrumbBar(currentPath = currentPath, rootPath = rootPath, onNavigateTo = onNavigateTo)
            SortBar(sort = sort, onSortChange = onSortChange)
            if (items.isEmpty()) {
                EmptyFolderState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.path }) { item ->
                        FileRow(item = item, onClick = { onOpen(item) }, onAction = { onAction(item, it) })
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootsScreen(
    onRootSelected: (StorageItem) -> Unit,
    onToggleTheme: () -> Unit,
    isDark: Boolean
) {
    val context = LocalContext.current
    val repository = remember { StorageRepository(context) }
    val roots = remember { repository.listRoots().ifEmpty { defaultRoots() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Manager +", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Storage locations", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            items(roots, key = { it.path }) { root ->
                StorageCard(root = root, onClick = { onRootSelected(root) })
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text("Browse files and folders on your device.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StorageCard(root: StorageItem, onClick: () -> Unit) {
    val icon = when {
        root.name.contains("Internal", ignoreCase = true) || root.path.contains("emulated") -> Icons.Default.PhoneAndroid
        root.name.contains("SD", ignoreCase = true) || root.path.contains("sdcard", ignoreCase = true) -> Icons.Default.SdCard
        else -> Icons.Default.Storage
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(root.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(root.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BreadcrumbBar(currentPath: String, rootPath: String?, onNavigateTo: (String) -> Unit) {
    val segments = buildBreadcrumbSegments(currentPath, rootPath)
    if (segments.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, seg ->
            if (index > 0) {
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val isLast = index == segments.lastIndex
            Text(
                text = seg.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !isLast) { onNavigateTo(seg.path) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

private data class PathSegment(val name: String, val path: String)

private fun buildBreadcrumbSegments(currentPath: String, rootPath: String?): List<PathSegment> {
    val root = rootPath ?: return emptyList()
    val segments = mutableListOf<PathSegment>()
    var path = currentPath
    while (path.length >= root.length) {
        val name = File(path).name.ifBlank { "Storage" }
        segments.add(0, PathSegment(name, path))
        if (path == root) break
        val parent = File(path).parent ?: break
        path = parent
    }
    return segments.takeLast(4) // keep it compact
}

@Composable
private fun SortBar(sort: StorageSort, onSortChange: (StorageSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box {
            TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Icon(Icons.Default.SwapVert, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sort: ${sort.label()}", style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                StorageSort.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label()) },
                        leadingIcon = { if (option == sort) Icon(Icons.Default.Check, null) },
                        onClick = { onSortChange(option); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFolderState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))
            Text("This folder is empty", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileRow(item: StorageItem, onClick: () -> Unit, onAction: (FileAction) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val (icon, tint) = fileIconAndTint(item)
    val subtitle = buildString {
        if (item.isDirectory) {
            append("Folder")
        } else {
            append(if (item.sizeKnown) formatSize(item.size) else "—")
            if (item.lastModified > 0) append("  •  ${formatShortDate(item.lastModified)}")
        }
    }

    ListItem(
        headlineContent = {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Options") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    FileAction.values().forEach { action ->
                        val (label, actionIcon) = action.labelAndIcon()
                        DropdownMenuItem(
                            text = { Text(label, color = if (action == FileAction.DELETE) MaterialTheme.colorScheme.error else Color.Unspecified) },
                            leadingIcon = { Icon(actionIcon, null, tint = if (action == FileAction.DELETE) MaterialTheme.colorScheme.error else LocalContentColor.current) },
                            onClick = { menuExpanded = false; onAction(action) }
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// --- TV layout (kept functional, improved readability) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvFileManagerScreen(
    currentPath: String, rootPath: String?, items: List<StorageItem>,
    onNavigateTo: (String) -> Unit, onGoUp: () -> Unit, onOpen: (StorageItem) -> Unit,
    onAction: (StorageItem, FileAction) -> Unit, onBack: () -> Unit,
    onToggleTheme: () -> Unit, isDark: Boolean,
    searchQuery: String, onSearchChange: (String) -> Unit,
    showHidden: Boolean, onShowHiddenChange: (Boolean) -> Unit,
    sort: StorageSort, onSortChange: (StorageSort) -> Unit,
    onCreateFolder: () -> Unit, onStorageInfo: () -> Unit
) {
    val folderName = File(currentPath).name.ifBlank { currentPath }
    Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("File Manager +", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = searchQuery, onValueChange = onSearchChange, label = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = onGoUp, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ArrowUpward, null); Spacer(Modifier.width(8.dp)); Text("Up") }
            OutlinedButton(onClick = onCreateFolder, modifier = Modifier.fillMaxWidth()) { Text("New folder") }
            OutlinedButton(onClick = onStorageInfo, modifier = Modifier.fillMaxWidth()) { Text("Storage usage") }
            OutlinedButton(onClick = { onShowHiddenChange(!showHidden) }, modifier = Modifier.fillMaxWidth()) { Text(if (showHidden) "Hide hidden" else "Show hidden") }
            OutlinedButton(onClick = onToggleTheme, modifier = Modifier.fillMaxWidth()) { Text(if (isDark) "Light theme" else "Dark theme") }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Storage locations") }
        }
        Column(Modifier.weight(1f)) {
            Text(folderName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            BreadcrumbBar(currentPath = currentPath, rootPath = rootPath, onNavigateTo = onNavigateTo)
            SortBar(sort = sort, onSortChange = onSortChange)
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                EmptyFolderState()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items, key = { it.path }) { item -> TvFileRow(item, onClick = { onOpen(item) }, onAction = { onAction(item, it) }) }
                }
            }
        }
    }
}

@Composable
private fun TvFileRow(item: StorageItem, onClick: () -> Unit, onAction: (FileAction) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val (icon, tint) = fileIconAndTint(item)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (item.isDirectory) "Folder" else if (item.sizeKnown) formatSize(item.size) else "—",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Options") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    FileAction.values().forEach { action ->
                        val (label, actionIcon) = action.labelAndIcon()
                        DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(actionIcon, null) },
                            onClick = { menuExpanded = false; onAction(action) })
                    }
                }
            }
        }
    }
}

// --- Helpers ---

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun fileIconAndTint(item: StorageItem): Pair<ImageVector, Color> {
    if (item.isDirectory) return Icons.Default.Folder to Color(0xFFF9A825)
    val name = item.name.lowercase()
    val mime = item.mimeType.orEmpty().lowercase()
    return when {
        mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
            name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") -> Icons.Default.Image to Color(0xFF43A047)
        mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
            name.endsWith(".avi") || name.endsWith(".mov") -> Icons.Default.Movie to Color(0xFFE53935)
        mime.startsWith("audio/") || name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav") ||
            name.endsWith(".flac") || name.endsWith(".ogg") || name.endsWith(".aac") -> Icons.Default.AudioFile to Color(0xFF8E24AA)
        name.endsWith(".pdf") -> Icons.Default.PictureAsPdf to Color(0xFFE53935)
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") ||
            name.endsWith(".gz") -> Icons.Default.FolderZip to Color(0xFF6D4C41)
        name.endsWith(".apk") -> Icons.Default.Android to Color(0xFF43A047)
        name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log") || name.endsWith(".json") ||
            name.endsWith(".xml") || name.endsWith(".csv") -> Icons.Default.Description to Color(0xFF1E88E5)
        mime.startsWith("text/") -> Icons.Default.Description to Color(0xFF1E88E5)
        else -> Icons.Default.InsertDriveFile to Color(0xFF546E7A)
    }
}

private fun FileAction.labelAndIcon(): Pair<String, ImageVector> = when (this) {
    FileAction.DETAILS -> "Details" to Icons.Outlined.Info
    FileAction.RENAME -> "Rename" to Icons.Outlined.Edit
    FileAction.COPY -> "Copy to…" to Icons.Outlined.FileCopy
    FileAction.MOVE -> "Move to…" to Icons.Outlined.DriveFileMove
    FileAction.DELETE -> "Delete" to Icons.Outlined.Delete
}

private fun StorageSort.label(): String = when (this) {
    StorageSort.NAME -> "Name"
    StorageSort.DATE -> "Date"
    StorageSort.SIZE -> "Size"
    StorageSort.TYPE -> "Type"
}

private fun isPlayable(item: StorageItem): Boolean {
    val mime = item.mimeType.orEmpty()
    return mime.startsWith("audio/") || mime.startsWith("video/") ||
        item.name.substringAfterLast('.', "").lowercase() in
        setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "mp4", "mkv", "webm", "avi")
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do { value /= 1024; index++ } while (value >= 1024 && index < units.lastIndex)
    return "${DecimalFormat("#,##0.#").format(value)} ${units[index]}"
}

private fun formatShortDate(millis: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))

private fun formatFullDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

private fun defaultRoots(): List<StorageItem> {
    val path = android.os.Environment.getExternalStorageDirectory().absolutePath
    return listOf(StorageItem(path = path, name = "Internal Storage", isDirectory = true, size = 0, mimeType = null))
}
