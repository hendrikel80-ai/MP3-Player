package de.speicherputz.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.speicherputz.scan.Category
import de.speicherputz.scan.Safety

fun Category.icon(): ImageVector = when (this) {
    Category.TRASH -> Icons.Filled.DeleteSweep
    Category.THUMBNAILS -> Icons.Filled.PhotoLibrary
    Category.TEMP -> Icons.Filled.HourglassEmpty
    Category.EMPTY_DIRS -> Icons.Filled.FolderOff
    Category.APK -> Icons.Filled.Android
    Category.MESSENGER -> Icons.Filled.Forum
    Category.OLD_DOWNLOADS -> Icons.Filled.Download
    Category.LARGE -> Icons.Filled.Storage
    Category.DUPLICATES -> Icons.Filled.ContentCopy
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    bottomBar: @Composable () -> Unit,
    onRequestAccess: () -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenCategory: (Category) -> Unit,
    onOpenAppCaches: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Speicherputz", fontWeight = FontWeight.Bold) }) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.storage?.let { item { StorageCard(it.usedBytes, it.totalBytes, it.freeBytes, it.usedFraction) } }

            if (!state.hasStorageAccess) {
                item { AccessCard(onRequestAccess) }
            } else {
                when (val scan = state.scan) {
                    ScanState.Idle -> item { StartCard(onScan) }
                    is ScanState.Scanning -> item { ScanningCard(scan, onCancelScan) }
                    is ScanState.Failed -> item {
                        InfoCard(Icons.Filled.Refresh, "Suche fehlgeschlagen", scan.message) {
                            Button(onClick = onScan) { Text("Erneut versuchen") }
                        }
                    }
                    is ScanState.Done -> {
                        val result = scan.result
                        val unique = result.uniqueItems().values
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Gefunden: ${formatBytes(unique.sumOf { it.size })}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "${plural(unique.size, "Eintrag", "Einträge")} · ${result.filesScanned} Dateien geprüft. " +
                                            "Tippe eine Kategorie an und wähle aus, was weg darf.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = onScan) {
                                    Icon(Icons.Filled.Refresh, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Neu")
                                }
                            }
                        }
                        if (result.items.isEmpty()) {
                            item {
                                InfoCard(Icons.Filled.Shield, "Alles sauber", "Es wurden keine überflüssigen Dateien gefunden.")
                            }
                        }
                        val categories = Category.entries.filter { result.of(it).isNotEmpty() }
                        items(categories, key = { it.name }) { category ->
                            val list = result.of(category)
                            CategoryCard(
                                category = category,
                                count = list.size,
                                bytes = list.sumOf { it.size },
                                selected = state.selectedIn(category),
                                onClick = { onOpenCategory(category) },
                            )
                        }
                    }
                }
            }

            item {
                NavCard(
                    icon = Icons.Filled.Apps,
                    title = "App-Zwischenspeicher (Cache)",
                    subtitle = "Zeigt, welche Apps viel Cache angesammelt haben, und führt dich direkt zu „Cache leeren“.",
                    onClick = onOpenAppCaches,
                )
            }
            item {
                InfoCard(
                    Icons.Filled.Lock,
                    "Was diese App nie anfasst",
                    "• Nichts wird automatisch gelöscht – nur was du auswählst und bestätigst.\n" +
                        "• Systemdateien, installierte Apps und deren Daten (Android/data, Android/obb).\n" +
                        "• Standardordner wie DCIM, Download, Music und „.nomedia“-Dateien.\n" +
                        "• Dateien, die sich seit der Suche verändert haben, werden übersprungen.",
                )
            }
        }
    }
}

@Composable
private fun StorageCard(used: Long, total: Long, free: Long, fraction: Float) {
    val critical = fraction >= 0.9f
    val barColor = if (critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${(fraction * 100).toInt()} %",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                )
                Spacer(Modifier.width(8.dp))
                Text("belegt", modifier = Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${formatBytes(used)} von ${formatBytes(total)} belegt · ${formatBytes(free)} frei",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccessCard(onRequestAccess: () -> Unit) {
    InfoCard(
        Icons.Filled.Lock,
        "Zugriff auf Dateien erlauben",
        "Damit die App überflüssige Dateien finden kann, braucht sie Zugriff auf den Speicher. " +
            "Im nächsten Schritt den Schalter bei „Speicherputz“ einschalten und mit „Zurück“ hierher zurückkehren.\n\n" +
            "Es wird nichts gelöscht, ohne dass du es auswählst und bestätigst.",
    ) {
        Button(onClick = onRequestAccess) { Text("Zugriff erlauben") }
    }
}

@Composable
private fun StartCard(onScan: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Durchsucht den Speicher nach unsichtbaren Vorschaubildern, Papierkorb-Resten, temporären Dateien, " +
                    "alten Installationsdateien, Duplikaten und großen Dateien.",
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Search, null)
                Spacer(Modifier.width(8.dp))
                Text("Speicher analysieren", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Die Suche löscht nichts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScanningCard(scan: ScanState.Scanning, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("${scan.filesScanned} Dateien geprüft", fontWeight = FontWeight.SemiBold)
            Text(
                scan.currentDir.ifEmpty { "Starte …" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCancel) { Text("Abbrechen") }
        }
    }
}

@Composable
fun SafetyBadge(safety: Safety) {
    val (bg, fg) = when (safety) {
        Safety.SAFE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        Safety.REVIEW -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            safety.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

@Composable
private fun CategoryCard(category: Category, count: Int, bytes: Long, selected: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(category.icon(), if (category.safety == Safety.SAFE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(category.title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SafetyBadge(category.safety)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selected > 0) "$selected von $count ausgewählt" else plural(count, "Eintrag", "Einträge"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(formatBytes(bytes), fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun IconBubble(icon: ImageVector, tint: Color) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = tint.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {}
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun NavCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InfoCard(
    icon: ImageVector,
    title: String,
    text: String,
    action: (@Composable () -> Unit)? = null,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.height(12.dp))
                action()
            }
        }
    }
}
