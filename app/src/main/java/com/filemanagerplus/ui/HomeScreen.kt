package com.filemanagerplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
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

