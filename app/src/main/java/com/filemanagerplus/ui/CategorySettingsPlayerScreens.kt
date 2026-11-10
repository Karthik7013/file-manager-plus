package com.filemanagerplus.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filemanagerplus.data.FileCategory
import com.filemanagerplus.data.StorageItem
import com.filemanagerplus.data.StorageRepository
import com.filemanagerplus.data.StorageSort
import com.filemanagerplus.media.MediaPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryScreen(repository: StorageRepository, scope: kotlinx.coroutines.CoroutineScope, cat: FileCategory,
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
internal fun SettingsScreen(showHidden: Boolean, onShowHidden: (Boolean) -> Unit, sort: StorageSort, onSort: (StorageSort) -> Unit,
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
internal fun PlayerScreen(playback: MediaPlaybackController, item: StorageItem?, onBack: () -> Unit) {
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
internal fun TvApp(
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
