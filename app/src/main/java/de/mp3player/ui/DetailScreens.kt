@file:OptIn(ExperimentalMaterial3Api::class)

package de.mp3player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mp3player.data.GroupType
import de.mp3player.ui.theme.EyebrowStyle

@Composable
private fun BackTopBar(title: String, actions: UiActions, extra: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = actions.back) { Icon(Icons.Outlined.ArrowBack, "Zurück") }
        },
        actions = { extra() },
    )
}

/** Detailseite für Künstler, Album oder Genre – eine automatische Playlist aus den Metadaten. */
@Composable
fun GroupDetailScreen(type: GroupType, key: String, actions: UiActions) {
    val vm = actions.vm
    val library by vm.library.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    val group = library.group(type, key)
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            BackTopBar(group?.title ?: type.label, actions) {
                if (group != null && group.songs.any { it.isMp3 }) {
                    IconButton(onClick = { actions.editTags(group.songs) }) {
                        Icon(Icons.Outlined.Edit, "Alle Titel bearbeiten")
                    }
                }
            }
        },
    ) { padding ->
        if (group == null) {
            Box(Modifier.padding(padding)) { EmptyState("Nichts gefunden") }
        } else {
            val albums = if (type == GroupType.ARTIST) {
                val keys = group.songs.map { it.albumKey }.toSet()
                library.albums.filter { it.key in keys }
            } else {
                emptyList()
            }
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    CollectionHeader(group.title, "${type.label} · ${group.subtitle}", group.cover, group.songs, actions) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { saving = true }) {
                                Icon(Icons.Outlined.Save, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Als Playlist speichern")
                            }
                            TextButton(onClick = { actions.addToPlaylist(group.songs) }) {
                                Icon(Icons.Outlined.PlaylistAdd, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Zu Playlist…")
                            }
                        }
                    }
                }
                if (albums.size > 1) {
                    item {
                        Column {
                        Text(
                            "Alben", style = EyebrowStyle,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(albums, key = { it.key }) { a ->
                                AlbumCard(a, Modifier.width(130.dp)) { actions.openGroup(GroupType.ALBUM, a.key) }
                            }
                        }
                        HorizontalDivider()
                        }
                    }
                }
                itemsIndexed(group.songs, key = { _, s -> s.id }) { i, s ->
                    val subtitle = when (type) {
                        GroupType.ALBUM -> buildString {
                            if (s.trackNo > 0) append("${s.trackNo}. ")
                            append(s.artist).append(" · ").append(formatTime(s.durationMs))
                        }
                        GroupType.ARTIST -> "${s.album} · ${formatTime(s.durationMs)}"
                        GroupType.GENRE -> "${s.artist} · ${s.album}"
                    }
                    SongRow(s, s.id == player.currentId, onClick = { vm.play(group.songs, i) }, subtitle = subtitle) { close ->
                        SongMenuItems(listOf(s), actions, close)
                    }
                }
            }
            if (saving) {
                NameDialog(
                    "Als Playlist speichern", initial = group.title, confirmLabel = "Erstellen",
                    onDismiss = { saving = false },
                ) { vm.createPlaylist(it, group.songs); saving = false }
            }
        }
    }
}

/** Eigene Playlist: abspielen, umbenennen, löschen, Titel sortieren und entfernen. */
@Composable
fun PlaylistDetailScreen(id: Long, actions: UiActions) {
    val vm = actions.vm
    val library by vm.library.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    val scanned by vm.scanned.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == id }
    val songs = remember(playlist, library) { playlist?.songIds?.mapNotNull { library.songsById[it] } ?: emptyList() }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(playlist == null) {
        if (playlist == null && playlists.isNotEmpty()) actions.back()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            BackTopBar(playlist?.name ?: "Playlist", actions) {
                if (playlist != null) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "Umbenennen") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "Löschen") }
                }
            }
        },
    ) { padding ->
        if (playlist == null) {
            Box(Modifier.padding(padding)) { EmptyState("Playlist nicht gefunden") }
        } else {
            val missing = playlist.songIds.size - songs.size
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    CollectionHeader(
                        playlist.name,
                        "Playlist · ${songs.size} Titel · ${formatTime(songs.sumOf { it.durationMs })}",
                        songs.firstOrNull(), songs, actions,
                    ) {
                        if (missing > 0 && scanned) {
                            Text(
                                "$missing Titel sind nicht mehr auf diesem Gerät.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                if (songs.isEmpty()) {
                    item {
                        Text(
                            "Diese Playlist ist noch leer. Füge Titel über das ⋮-Menü bei einem Song hinzu " +
                                "oder über „Zu Playlist…“ auf einer Künstler-, Album- oder Genre-Seite.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, s.id == player.currentId, onClick = { vm.play(songs, i) }) { close ->
                        DropdownMenuItem(text = { Text("Nach oben") }, enabled = i > 0,
                            onClick = { vm.moveInPlaylist(id, s, -1); close() })
                        DropdownMenuItem(text = { Text("Nach unten") }, enabled = i < songs.lastIndex,
                            onClick = { vm.moveInPlaylist(id, s, 1); close() })
                        DropdownMenuItem(text = { Text("Aus Playlist entfernen") },
                            onClick = { vm.removeFromPlaylist(id, s); close() })
                        HorizontalDivider()
                        SongMenuItems(listOf(s), actions, close)
                    }
                }
            }
            if (renaming) {
                NameDialog("Playlist umbenennen", initial = playlist.name, onDismiss = { renaming = false }) {
                    vm.renamePlaylist(id, it); renaming = false
                }
            }
            if (deleting) {
                ConfirmDialog(
                    "Playlist löschen?", "„${playlist.name}“ wird gelöscht. Die Musikdateien bleiben erhalten.", "Löschen",
                    onDismiss = { deleting = false },
                ) {
                    deleting = false
                    vm.deletePlaylist(id)
                    actions.back()
                }
            }
        }
    }
}
