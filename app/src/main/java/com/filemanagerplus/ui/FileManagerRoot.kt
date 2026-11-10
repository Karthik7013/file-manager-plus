package com.filemanagerplus.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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


@Composable
fun FileManagerRoot(isTv: Boolean, onToggleTheme: () -> Unit, isDark: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fm_prefs", Context.MODE_PRIVATE) }
    val repository = remember { StorageRepository(context) }
    val playback = remember { MediaPlaybackController(context) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(Screen.HOME) }
    var browserPath by remember { mutableStateOf<String?>(null) }
    var browserRoot by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<FileCategory?>(null) }
    var playingItem by remember { mutableStateOf<StorageItem?>(null) }

    var showHidden by remember { mutableStateOf(prefs.getBoolean("show_hidden", false)) }
    var sort by remember { mutableStateOf(StorageSort.valueOf(prefs.getString("sort", StorageSort.NAME.name)!!)) }
    var gridView by remember { mutableStateOf(prefs.getBoolean("grid_view", false)) }

    fun setShowHidden(v: Boolean) { showHidden = v; prefs.edit().putBoolean("show_hidden", v).apply() }
    fun setSort(v: StorageSort) { sort = v; prefs.edit().putString("sort", v.name).apply() }
    fun setGridView(v: Boolean) { gridView = v; prefs.edit().putBoolean("grid_view", v).apply() }

    fun openBrowser(path: String, root: String? = null) {
        browserPath = path
        browserRoot = root ?: browserRoot ?: path
        screen = Screen.BROWSER
    }

    fun openCategory(cat: FileCategory) { category = cat; screen = Screen.CATEGORY }
    fun openPlayer(item: StorageItem) { playingItem = item; screen = Screen.PLAYER }

    DisposableEffect(Unit) { onDispose { playback.release() } }

    val back: () -> Unit = {
        when (screen) {
            Screen.HOME -> { /* no-op at root */ }
            Screen.BROWSER -> {
                val cur = browserPath; val root = browserRoot
                if (cur != null && root != null && cur != root) {
                    browserPath = File(cur).parent ?: root
                } else screen = Screen.HOME
            }
            Screen.CATEGORY, Screen.SETTINGS, Screen.PLAYER -> screen = Screen.HOME
        }
    }

    if (isTv) {
        TvApp(
            screen = screen, onNavigate = { screen = it },
            repository = repository, playback = playback, scope = scope,
            browserPath = browserPath, browserRoot = browserRoot,
            onOpenBrowser = ::openBrowser, onSetBrowserPath = { browserPath = it },
            category = category, onOpenCategory = ::openCategory,
            playingItem = playingItem, onPlay = ::openPlayer,
            showHidden = showHidden, onShowHidden = ::setShowHidden,
            sort = sort, onSort = ::setSort,
            onToggleTheme = onToggleTheme, isDark = isDark, onBack = back
        )
    } else {
        MobileApp(
            screen = screen, onNavigate = { screen = it },
            repository = repository, playback = playback, scope = scope,
            browserPath = browserPath, browserRoot = browserRoot,
            onOpenBrowser = ::openBrowser, onSetBrowserPath = { browserPath = it },
            category = category, onOpenCategory = ::openCategory,
            playingItem = playingItem, onPlay = ::openPlayer,
            showHidden = showHidden, onShowHidden = ::setShowHidden,
            sort = sort, onSort = ::setSort, gridView = gridView, onGridView = ::setGridView,
            onToggleTheme = onToggleTheme, isDark = isDark, onBack = back
        )
    }
}

@Composable
private fun MobileApp(
    screen: Screen, onNavigate: (Screen) -> Unit,
    repository: StorageRepository, playback: MediaPlaybackController, scope: kotlinx.coroutines.CoroutineScope,
    browserPath: String?, browserRoot: String?,
    onOpenBrowser: (String, String?) -> Unit, onSetBrowserPath: (String) -> Unit,
    category: FileCategory?, onOpenCategory: (FileCategory) -> Unit,
    playingItem: StorageItem?, onPlay: (StorageItem) -> Unit,
    showHidden: Boolean, onShowHidden: (Boolean) -> Unit,
    sort: StorageSort, onSort: (StorageSort) -> Unit,
    gridView: Boolean, onGridView: (Boolean) -> Unit,
    onToggleTheme: () -> Unit, isDark: Boolean, onBack: () -> Unit
) {
    val tabs = listOf(Screen.HOME, Screen.BROWSER, Screen.SETTINGS)
    Scaffold(
        bottomBar = {
            if (screen != Screen.PLAYER) {
                NavigationBar {
                    NavigationBarItem(selected = screen == Screen.HOME, onClick = { onNavigate(Screen.HOME) },
                        icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(selected = screen == Screen.BROWSER || screen == Screen.CATEGORY,
                        onClick = { if (browserPath != null) onNavigate(Screen.BROWSER) else onOpenBrowser(repository.internalStoragePath(), repository.internalStoragePath()) },
                        icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Files") })
                    NavigationBarItem(selected = screen == Screen.SETTINGS, onClick = { onNavigate(Screen.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(repository, scope, onOpenBrowser = { onOpenBrowser(it, it) },
                    onOpenPath = { path, root -> onOpenBrowser(path, root) },
                    onOpenCategory = onOpenCategory, onPlay = onPlay)
                Screen.BROWSER -> BrowserScreen(repository, scope, playback,
                    currentPath = browserPath ?: repository.internalStoragePath(),
                    rootPath = browserRoot, onNavigate = onSetBrowserPath,
                    onOpenCategory = onOpenCategory, onPlay = onPlay,
                    showHidden = showHidden, sort = sort, gridView = gridView,
                    onToggleGrid = { onGridView(!gridView) }, onBack = onBack)
                Screen.CATEGORY -> CategoryScreen(repository, scope, category ?: FileCategory.IMAGES,
                    onOpen = { item -> if (item.isDirectory) onOpenBrowser(item.path, null) else onPlay(item) },
                    onBack = { onNavigate(Screen.HOME) })
                Screen.SETTINGS -> SettingsScreen(showHidden, onShowHidden, sort, onSort, gridView, onGridView, isDark, onToggleTheme)
                Screen.PLAYER -> PlayerScreen(playback, playingItem, onBack = { onNavigate(Screen.HOME) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    repository: StorageRepository, scope: kotlinx.coroutines.CoroutineScope,
    onOpenBrowser: (String) -> Unit, onOpenPath: (String, String) -> Unit,
    onOpenCategory: (FileCategory) -> Unit, onPlay: (StorageItem) -> Unit
) {
    val internal = remember { repository.internalStoragePath() }
    var stats by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var recents by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    var loadingRecents by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.IO) { repository.storageStats(internal) }
        recents = withContext(Dispatchers.IO) { repository.listRecent(internal, 12) }
        loadingRecents = false
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("File Manager +", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

        item {
            StorageSummaryCard(stats = stats, onClick = { onOpenBrowser(internal) })
        }

        item { SectionTitle("Categories") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(FileCategory.values().toList()) { cat ->
                    CategoryChip(cat) { onOpenCategory(cat) }
                }
            }
        }

        item { SectionTitle("Storage locations") }
        items(repository.listRoots().ifEmpty {
            listOf(StorageItem(internal, "Internal Storage", true, 0, null))
        }, key = { it.path }) { root ->
            LocationRow(root) { onOpenPath(root.path, root.path) }
        }

        item { SectionTitle("Recent files") }
        if (loadingRecents) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (recents.isEmpty()) {
            item { Text("No recent files found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(recents, key = { it.path }) { item ->
                RecentRow(item, onClick = {
                    if (isPlayable(item)) onPlay(item) else { /* open via browser context not direct; navigate to parent */ }
                })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun StorageSummaryCard(stats: Pair<Long, Long>?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Internal Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (stats != null) {
                        val (total, used) = stats
                        val pct = if (total > 0) used.toFloat() / total else 0f
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(4.dp))
                        Text("${formatSize(used)} used of ${formatSize(total)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        Text("Tap to browse", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(cat: FileCategory, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(cat.color().copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center) {
            Icon(cat.icon(), null, tint = cat.color(), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(cat.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun LocationRow(root: StorageItem, onClick: () -> Unit) {
    val icon = if (root.path.contains("emulated") || root.name.contains("Internal", true)) Icons.Default.PhoneAndroid else Icons.Default.SdStorage
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(root.name, fontWeight = FontWeight.Medium)
                Text(root.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentRow(item: StorageItem, onClick: () -> Unit) {
    val (icon, tint) = fileIconAndTint(item)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(formatShortDate(item.lastModified), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


// --- Category, Settings, Player, TV ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryScreen(repository: StorageRepository, scope: kotlinx.coroutines.CoroutineScope, cat: FileCategory,
    onOpen: (StorageItem) -> Unit, onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val root = remember { repository.internalStoragePath() }
    LaunchedEffect(cat) {
        loading = true
        items = withContext(Dispatchers.IO) { repository.listByCategory(root, cat) }
        loading = false
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(cat.label) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
    }) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { EmptyState("No ${cat.label.lowercase()} found") }
            else -> LazyColumn(Modifier.padding(padding)) {
                items(items, key = { it.path }) { item ->
                    val (icon, tint) = fileIconAndTint(item)
                    ListItem(
                        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${formatSize(item.size)} • ${formatShortDate(item.lastModified)}", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) } },
                        modifier = Modifier.clickable { onOpen(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(showHidden: Boolean, onShowHidden: (Boolean) -> Unit, sort: StorageSort, onSort: (StorageSort) -> Unit,
    gridView: Boolean, onGridView: (Boolean) -> Unit, isDark: Boolean, onToggleTheme: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            item { SettingsSwitch("Dark theme", "Use dark color scheme", isDark) { _ -> onToggleTheme() } }
            item { HorizontalDivider() }
            item { Text("File browsing", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            item { SettingsSwitch("Show hidden files", "Display files starting with .", showHidden, { onShowHidden(it) }) }
            item { SettingsSwitch("Grid view", "Show files in a grid", gridView, { onGridView(it) }) }
            item {
                var expanded by remember { mutableStateOf(false) }
                ListItem(headlineContent = { Text("Default sort") }, supportingContent = { Text(sort.label()) },
                    modifier = Modifier.clickable { expanded = true },
                    trailingContent = { Box { TextButton(onClick = { expanded = true }) { Text(sort.label()) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            StorageSort.values().forEach { s -> DropdownMenuItem(text = { Text(s.label()) }, onClick = { onSort(s); expanded = false }) }
                        } } })
            }
            item { HorizontalDivider() }
            item { Text("About", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            item { ListItem(headlineContent = { Text("File Manager +") }, supportingContent = { Text("Version 0.1.0 • Open source (MIT)") }) }
            item { ListItem(headlineContent = { Text("Permissions") }, supportingContent = { Text("Uses MANAGE_EXTERNAL_STORAGE for full file access. No ads, no tracking.") }) }
        }
    }
}

@Composable private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChecked) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(playback: MediaPlaybackController, item: StorageItem?, onBack: () -> Unit) {
    var isPlaying by remember { mutableStateOf(true) }
    LaunchedEffect(item) { item?.let { runCatching { playback.play(Uri.fromFile(File(it.path))) } } }
    Scaffold(topBar = { TopAppBar(title = { Text("Now playing") }, navigationIcon = { IconButton(onClick = { playback.player.pause(); onBack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        if (item == null) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("No media selected") }; return@Scaffold }
        val (icon, tint) = fileIconAndTint(item)
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(160.dp).clip(RoundedCornerShape(24.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(80.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(item.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (isPlayable(item) && item.mimeType?.startsWith("video") == true) "Video" else "Audio", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = { playback.player.seekBack() }) { Icon(Icons.Default.Replay10, "Back 10s") }
                FilledIconButton(onClick = { if (isPlaying) playback.player.pause() else playback.player.play(); isPlaying = !isPlaying }, modifier = Modifier.size(64.dp)) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", modifier = Modifier.size(32.dp))
                }
                FilledIconButton(onClick = { playback.player.seekForward() }) { Icon(Icons.Default.Forward10, "Forward 10s") }
            }
            Spacer(Modifier.height(16.dp))
            Text("Basic playback • seek/queue/background service pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TvApp(
    screen: Screen, onNavigate: (Screen) -> Unit,
    repository: StorageRepository, playback: MediaPlaybackController, scope: kotlinx.coroutines.CoroutineScope,
    browserPath: String?, browserRoot: String?,
    onOpenBrowser: (String, String?) -> Unit, onSetBrowserPath: (String) -> Unit,
    category: FileCategory?, onOpenCategory: (FileCategory) -> Unit,
    playingItem: StorageItem?, onPlay: (StorageItem) -> Unit,
    showHidden: Boolean, onShowHidden: (Boolean) -> Unit,
    sort: StorageSort, onSort: (StorageSort) -> Unit,
    onToggleTheme: () -> Unit, isDark: Boolean, onBack: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        // TV sidebar
        NavigationRail(modifier = Modifier.width(100.dp)) {
            NavigationRailItem(selected = screen == Screen.HOME, onClick = { onNavigate(Screen.HOME) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
            NavigationRailItem(selected = screen == Screen.BROWSER, onClick = { if (browserPath != null) onNavigate(Screen.BROWSER) else onOpenBrowser(repository.internalStoragePath(), repository.internalStoragePath()) }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Files") })
            FileCategory.values().forEach { cat ->
                NavigationRailItem(selected = screen == Screen.CATEGORY && category == cat, onClick = { onOpenCategory(cat) }, icon = { Icon(cat.icon(), null) }, label = { Text(cat.label, maxLines = 1) })
            }
            NavigationRailItem(selected = screen == Screen.SETTINGS, onClick = { onNavigate(Screen.SETTINGS) }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (screen) {
                Screen.HOME -> HomeScreen(repository, scope, { onOpenBrowser(it, it) }, { p, r -> onOpenBrowser(p, r) }, onOpenCategory, onPlay)
                Screen.BROWSER -> BrowserScreen(repository, scope, playback, browserPath ?: repository.internalStoragePath(), browserRoot, onSetBrowserPath, onOpenCategory, onPlay, showHidden, sort, false, {}, onBack)
                Screen.CATEGORY -> CategoryScreen(repository, scope, category ?: FileCategory.IMAGES, { if (it.isDirectory) onOpenBrowser(it.path, null) else onPlay(it) }, { onNavigate(Screen.HOME) })
                Screen.SETTINGS -> SettingsScreen(showHidden, onShowHidden, sort, onSort, false, {}, isDark, onToggleTheme)
                Screen.PLAYER -> PlayerScreen(playback, playingItem) { onNavigate(Screen.HOME) }
            }
        }
    }
}

// --- Shared UI components ---

@Composable
internal fun BreadcrumbBar(currentPath: String, rootPath: String?, onNavigate: (String) -> Unit) {
    val segments = buildList {
        val root = rootPath ?: return@buildList
        var p = currentPath
        while (p.length >= root.length) {
            add(p to File(p).name.ifBlank { "Storage" })
            if (p == root) break
            p = File(p).parent ?: break
        }
        reverse()
    }.takeLast(4)
    if (segments.size <= 1) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        segments.forEachIndexed { i, (path, name) ->
            if (i > 0) Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            val last = i == segments.lastIndex
            Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(enabled = !last) { onNavigate(path) }.padding(horizontal = 4.dp, vertical = 4.dp))
        }
    }
}

@Composable
internal fun SortBarRow(sort: StorageSort) {
    // sort is controlled by parent in this simplified UI; shown as label
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.SwapVert, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text("Sorted by ${sort.label()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EmptyState(msg: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))
            Text(msg, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun FileListRow(item: StorageItem, onClick: () -> Unit, onAction: (FileAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val (icon, tint) = fileIconAndTint(item)
    val sub = if (item.isDirectory) "Folder" else buildString { append(if (item.sizeKnown) formatSize(item.size) else "—"); if (item.lastModified > 0) append("  •  ${formatShortDate(item.lastModified)}") }
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) } },
        trailingContent = {
            Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    FileAction.values().forEach { a ->
                        val (lbl, ic) = actionUi(a)
                        DropdownMenuItem(text = { Text(lbl, color = if (a == FileAction.DELETE) MaterialTheme.colorScheme.error else Color.Unspecified) },
                            leadingIcon = { Icon(ic, null, tint = if (a == FileAction.DELETE) MaterialTheme.colorScheme.error else LocalContentColor.current) },
                            onClick = { menu = false; onAction(a) })
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
internal fun FileGridItem(item: StorageItem, onClick: () -> Unit, onAction: (FileAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val (icon, tint) = fileIconAndTint(item)
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.align(Alignment.End)) {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    FileAction.values().forEach { a -> val (lbl, ic) = actionUi(a); DropdownMenuItem(text = { Text(lbl) }, leadingIcon = { Icon(ic, null) }, onClick = { menu = false; onAction(a) }) }
                }
            }
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun NameDialog(title: String, value: String, onValue: (String) -> Unit, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onValue, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun DetailsDialog(item: StorageItem, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailRow("Type", if (item.isDirectory) "Folder" else item.mimeType ?: "File")
            DetailRow("Size", if (item.isDirectory) "—" else if (item.sizeKnown) formatSize(item.size) else "Unknown")
            DetailRow("Modified", formatFullDate(item.lastModified))
            DetailRow("Path", item.path)
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
internal fun UsageDialog(usage: StorageUsage?, loading: Boolean, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Storage usage") },
        text = {
            if (loading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (usage == null) Text("Could not calculate")
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Total size", formatSize(usage.totalBytes))
                DetailRow("Files", usage.fileCount.toString())
                DetailRow("Folders", usage.folderCount.toString())
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable internal fun DetailRow(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.bodyMedium) }
}

internal fun actionUi(a: FileAction): Pair<String, ImageVector> = when (a) {
    FileAction.DETAILS -> "Details" to Icons.Outlined.Info
    FileAction.RENAME -> "Rename" to Icons.Outlined.Edit
    FileAction.COPY -> "Copy to…" to Icons.Outlined.FileCopy
    FileAction.MOVE -> "Move to…" to Icons.Outlined.DriveFileMove
    FileAction.DELETE -> "Delete" to Icons.Outlined.Delete
}

