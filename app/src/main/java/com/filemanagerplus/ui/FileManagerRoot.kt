package com.filemanagerplus.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.filemanagerplus.data.FileCategory
import com.filemanagerplus.data.StorageItem
import com.filemanagerplus.data.StorageRepository
import com.filemanagerplus.data.StorageSort
import com.filemanagerplus.media.MediaPlaybackController
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



