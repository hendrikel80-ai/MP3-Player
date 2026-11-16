package de.speicherputz.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.speicherputz.system.Device

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCacheScreen(
    state: UiState,
    onBack: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onOpenApp: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App-Zwischenspeicher") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    if (state.hasUsageAccess) {
                        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                    }
                },
            )
        },
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
                InfoCard(
                    Icons.Filled.Info,
                    "Warum geht das nicht mit einem Knopf?",
                    "Android erlaubt keiner App, den Cache anderer Apps zu löschen – das kann nur das System selbst. " +
                        "Diese Liste zeigt dir, wo sich das Leeren lohnt, und bringt dich direkt zur richtigen Stelle.\n\n" +
                        "So geht's: „Cache leeren“ antippen → „Speicher“ (bzw. „Speicher und Cache“) → „Cache leeren“ → zurück.",
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Nur „Cache leeren“ antippen – NICHT „Speicher löschen“ / „Daten löschen“! " +
                                "Sonst wirst du in der App abgemeldet und Chats, Einstellungen oder Spielstände können verloren gehen.",
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            when {
                !state.hasUsageAccess -> item {
                    InfoCard(
                        Icons.Filled.Lock,
                        "Zugriff auf Nutzungsdaten erlauben",
                        "Um die Cache-Größe der Apps anzeigen zu können, braucht die App „Zugriff auf Nutzungsdaten“. " +
                            "Im nächsten Schritt „Speicherputz“ auswählen, Schalter einschalten und zurückkehren. " +
                            "Die App liest damit nur Speichergrößen – sie verschickt keine Daten.",
                    ) {
                        Button(onClick = onRequestUsageAccess) { Text("Zugriff erlauben") }
                    }
                }
                state.appCachesLoading && state.appCaches == null -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    val caches = state.appCaches.orEmpty()
                    item {
                        Text(
                            if (caches.isEmpty()) "Keine App hat nennenswerten Cache (über 1 MB)."
                            else "${plural(caches.size, "App", "Apps")} mit zusammen ${formatBytes(caches.sumOf { it.cacheBytes })} Cache",
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(caches, key = { it.packageName }) { app ->
                        AppRow(app, onOpen = { onOpenApp(app.packageName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: Device.AppCache, onOpen: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = app.icon
            if (icon != null) {
                Image(icon.asImageBitmap(), null, modifier = Modifier.size(40.dp))
            } else {
                IconBubble(Icons.Filled.Apps, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Cache: ${formatBytes(app.cacheBytes)}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "App + Daten: ${formatBytes(app.totalBytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilledTonalButton(onClick = onOpen) { Text("Cache leeren") }
        }
    }
}
