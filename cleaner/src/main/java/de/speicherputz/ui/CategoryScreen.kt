package de.speicherputz.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.speicherputz.scan.Category
import de.speicherputz.scan.FoundItem
import de.speicherputz.scan.Rules
import de.speicherputz.system.Device
import de.speicherputz.system.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    category: Category,
    state: UiState,
    bottomBar: @Composable () -> Unit,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val items = state.result?.of(category).orEmpty()
    val duplicates = state.result?.of(Category.DUPLICATES).orEmpty()

    fun toggle(item: FoundItem) {
        // Schutz: bei Duplikaten nie alle Kopien einer Gruppe auswählen
        if (item.path !in state.selected && item.groupId >= 0) {
            val wouldBeComplete = Rules.fullySelectedGroups(duplicates, state.selected + item.path).contains(item.groupId)
            if (wouldBeComplete) {
                scope.launch { snackbar.showSnackbar("Mindestens eine Kopie muss erhalten bleiben.") }
                return
            }
        }
        onToggle(item.path)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        SafetyBadge(category.safety)
                        Spacer(Modifier.height(8.dp))
                        Text(category.description)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            category.safety.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onSelectAll) {
                        Text(if (category == Category.DUPLICATES) "Alle Kopien auswählen" else "Alle auswählen")
                    }
                    OutlinedButton(onClick = onSelectNone) { Text("Keine") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${items.count { it.path in state.selected }}/${items.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (category == Category.DUPLICATES) {
                items.groupBy { it.groupId }.forEach { (groupId, group) ->
                    item(key = "g$groupId") {
                        Text(
                            "${group.size} gleiche Dateien à ${formatBytes(group.first().size)}",
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    items(group, key = { it.path }) { item ->
                        FileRow(item, item.path in state.selected, onToggle = { toggle(item) }, onOpen = {
                            Device.openFile(context, File(item.path))
                        })
                    }
                }
            } else {
                items(items, key = { it.path }) { item ->
                    FileRow(item, item.path in state.selected, onToggle = { toggle(item) }, onOpen = {
                        Device.openFile(context, File(item.path))
                    })
                }
            }
        }
    }
}

@Composable
private fun FileRow(item: FoundItem, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val container = if (checked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            FileThumb(item)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name.removePrefix(".trashed-"),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.isOriginal) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                            Text(
                                "Original",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Text(
                    "📁 " + item.folder.ifEmpty { "Hauptverzeichnis" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                Text(
                    (if (item.isDirectory) "Ordner" else formatBytes(item.size)) + " · " + formatDate(item.lastModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!item.isDirectory && Rules.extension(item.name) !in setOf("apk", "apks", "xapk", "apkm")) {
                IconButton(onClick = onOpen) {
                    Icon(Icons.Filled.Visibility, "Ansehen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FileThumb(item: FoundItem) {
    val name = item.name.removePrefix(".trashed-")
    val previewable = !item.isDirectory && (Thumbnails.isImage(name) || Thumbnails.isVideo(name))
    val bitmap by produceState<Bitmap?>(null, item.path) {
        if (previewable) value = withContext(Dispatchers.IO) { Thumbnails.load(item.path) }
    }
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            IconBubble(fileIcon(item, name), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun fileIcon(item: FoundItem, name: String): ImageVector = when {
    item.isDirectory -> Icons.Filled.Folder
    Thumbnails.isImage(name) -> Icons.Filled.Image
    Thumbnails.isVideo(name) -> Icons.Filled.Movie
    Rules.extension(name) in setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "amr") -> Icons.Filled.AudioFile
    Rules.extension(name) in setOf("apk", "apks", "xapk", "apkm") -> Icons.Filled.Android
    Rules.extension(name) in setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "log") -> Icons.Filled.Description
    else -> Icons.Filled.InsertDriveFile
}
