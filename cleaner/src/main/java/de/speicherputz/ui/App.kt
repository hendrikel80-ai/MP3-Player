package de.speicherputz.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.speicherputz.scan.Category
import de.speicherputz.scan.Safety
import de.speicherputz.system.Device

/** Welche Seite gerade angezeigt wird. */
private sealed interface Screen {
    data object Home : Screen
    data class Detail(val category: Category) : Screen
    data object AppCaches : Screen
}

@Composable
fun SpeicherputzApp(vm: CleanerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var screenName by rememberSaveable { mutableStateOf("home") }
    val screen: Screen = when {
        screenName == "caches" -> Screen.AppCaches
        screenName.startsWith("cat:") -> Category.entries.firstOrNull { it.name == screenName.removePrefix("cat:") }
            ?.let { Screen.Detail(it) } ?: Screen.Home
        else -> Screen.Home
    }
    var confirmDelete by remember { mutableStateOf(false) }

    // Nach Rückkehr aus den Android-Einstellungen Berechtigungen neu prüfen
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshStatus()
        if (screenName == "caches") vm.loadAppCaches()
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refreshStatus() }

    val requestStorageAccess = {
        val intent = Device.allFilesAccessIntent(context)
        if (intent != null) {
            Device.startSafely(context, intent, Device.allFilesAccessFallbackIntent())
        } else {
            legacyPermissionLauncher.launch(Device.legacyStoragePermissions)
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screenName = "home" }

    val bottomBar: @Composable () -> Unit = {
        if (state.selected.isNotEmpty() && screen != Screen.AppCaches) {
            SelectionBar(
                count = state.selected.size,
                bytes = state.selectedBytes(),
                onDelete = { confirmDelete = true },
            )
        }
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            state = state,
            bottomBar = bottomBar,
            onRequestAccess = { requestStorageAccess() },
            onScan = vm::startScan,
            onCancelScan = vm::cancelScan,
            onOpenCategory = { screenName = "cat:${it.name}" },
            onOpenAppCaches = {
                screenName = "caches"
                vm.loadAppCaches()
            },
        )
        is Screen.Detail -> CategoryScreen(
            category = s.category,
            state = state,
            bottomBar = bottomBar,
            onBack = { screenName = "home" },
            onToggle = vm::toggle,
            onSelectAll = { vm.selectAll(s.category) },
            onSelectNone = { vm.selectNone(s.category) },
        )
        Screen.AppCaches -> AppCacheScreen(
            state = state,
            onBack = { screenName = "home" },
            onRequestUsageAccess = { Device.startSafely(context, *Device.usageAccessIntents(context)) },
            onOpenApp = { Device.startSafely(context, Device.appDetailsIntent(it)) },
            onRefresh = vm::loadAppCaches,
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            state = state,
            blockedGroups = vm.fullySelectedDuplicateGroups(state).size,
            onConfirm = {
                confirmDelete = false
                vm.deleteSelected()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (state.deleting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Wird gelöscht …") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 16.dp))
                    Text("Bitte warten")
                }
            },
        )
    }
    state.lastReport?.let { report ->
        AlertDialog(
            onDismissRequest = vm::dismissReport,
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("${formatBytes(report.freedBytes)} freigegeben") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("${plural(report.deleted.size, "Eintrag", "Einträge")} gelöscht.")
                    if (report.failed.isNotEmpty()) {
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            "${plural(report.failed.size, "Eintrag wurde", "Einträge wurden")} zur Sicherheit nicht gelöscht:",
                            fontWeight = FontWeight.SemiBold,
                        )
                        report.failed.take(20).forEach { (path, reason) ->
                            Text("• $path – $reason", style = MaterialTheme.typography.bodySmall)
                        }
                        if (report.failed.size > 20) Text("… und ${report.failed.size - 20} weitere")
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissReport) { Text("OK") } },
        )
    }
}

@Composable
private fun SelectionBar(count: Int, bytes: Long, onDelete: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${plural(count, "Eintrag", "Einträge")} ausgewählt", fontWeight = FontWeight.SemiBold)
                Text(formatBytes(bytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Filled.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("Löschen …")
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(state: UiState, blockedGroups: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val items = state.selectedItems()
    val result = state.result
    val reviewCount = if (result == null) 0 else items.count { item ->
        Category.entries.any { c -> c.safety == Safety.REVIEW && result.of(c).any { it.path == item.path } }
    }
    if (blockedGroups > 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Alle Kopien ausgewählt") },
            text = {
                Text(
                    "Bei ${plural(blockedGroups, "Gruppe", "Gruppen")} doppelter Dateien hast du alle Kopien ausgewählt – " +
                        "die Datei wäre dann ganz weg. Bitte lass in jeder Gruppe mindestens eine Kopie abgewählt " +
                        "(z. B. das „Original“).",
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Endgültig löschen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${plural(items.size, "Eintrag", "Einträge")} (${formatBytes(items.sumOf { it.size })}) " +
                        "werden endgültig gelöscht. Das lässt sich nicht rückgängig machen.",
                )
                if (reviewCount > 0) {
                    Text(
                        "Darunter ${plural(reviewCount, "persönliche Datei", "persönliche Dateien")} aus „Bitte prüfen“-Kategorien. " +
                            "Hast du sie dir angesehen?",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
