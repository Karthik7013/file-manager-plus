package com.filemanagerplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filemanagerplus.data.StorageItem
import com.filemanagerplus.data.StorageSort
import com.filemanagerplus.data.StorageUsage
import java.io.File


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

