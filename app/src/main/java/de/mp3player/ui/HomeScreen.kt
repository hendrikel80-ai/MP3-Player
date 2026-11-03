@file:OptIn(ExperimentalMaterial3Api::class)

package de.mp3player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mp3player.data.GroupType
import de.mp3player.data.Library
import de.mp3player.data.MediaGroup
import de.mp3player.data.Playlist
import de.mp3player.data.Song
import de.mp3player.playback.PlayerState

enum class HomeTab(val label: String, val icon: ImageVector) {
    SONGS("Titel", Icons.Filled.MusicNote),
    ARTISTS("Künstler", Icons.Filled.Person),
    ALBUMS("Alben", Icons.Filled.Album),
    GENRES("Genres", Icons.Filled.Category),
    PLAYLISTS("Playlists", Icons.Filled.QueueMusic),
}

@Composable
fun HomeScreen(tab: HomeTab, actions: UiActions) {
    val vm = actions.vm
    val library by vm.library.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val scanned by vm.scanned.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Suchen…") }, singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(if (tab == HomeTab.SONGS) "MP3 Player" else tab.label)
                    }
                },
                navigationIcon = {
                    if (searching) {
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Suche schließen")
                        }
                    }
                },
                actions = {
                    if (searching && query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Leeren") }
                    }
                    if (!searching && tab != HomeTab.PLAYLISTS) {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Suchen") }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Musik neu einlesen") },
                                onClick = { menuOpen = false; vm.rescan() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                tab == HomeTab.PLAYLISTS -> PlaylistsTab(playlists, library, actions)
                library.songs.isEmpty() && (loading || !scanned) ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                library.songs.isEmpty() -> EmptyState(
                    "Keine Musik gefunden.\n\nKopiere deine MP3s auf das Handy, z. B. in den Ordner „Music“.",
                ) { Button(onClick = vm::rescan) { Text("Erneut suchen") } }
                else -> {
                    val q = query.trim()
                    when (tab) {
                        HomeTab.SONGS -> SongsTab(
                            if (q.isEmpty()) library.songs else library.songs.filter {
                                it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
                            },
                            player, actions,
                        )
                        HomeTab.ARTISTS -> GroupList(library.artists.filterBy(q), round = true, actions)
                        HomeTab.ALBUMS -> AlbumGrid(library.albums.filterBy(q), actions)
                        HomeTab.GENRES -> GroupList(library.genres.filterBy(q), round = false, actions)
                        HomeTab.PLAYLISTS -> Unit
                    }
                }
            }
        }
    }
}

private fun List<MediaGroup>.filterBy(q: String) =
    if (q.isEmpty()) this else filter { it.title.contains(q, true) || it.subtitle.contains(q, true) }

@Composable
private fun SongsTab(songs: List<Song>, player: PlayerState, actions: UiActions) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            FilledTonalButton(
                onClick = { actions.vm.shuffle(songs) },
                enabled = songs.isNotEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Shuffle, null)
                Spacer(Modifier.width(8.dp))
                Text("Alle ${songs.size} Titel zufällig")
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(s, s.id == player.currentId, onClick = { actions.vm.play(songs, i) }) { close ->
                SongMenuItems(listOf(s), actions, close)
            }
        }
    }
}

@Composable
private fun GroupList(groups: List<MediaGroup>, round: Boolean, actions: UiActions) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.key }) { g ->
            GroupRow(g, onClick = { actions.openGroup(g.type, g.key) }, round = round)
        }
    }
}

@Composable
fun AlbumGrid(albums: List<MediaGroup>, actions: UiActions) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.key }) { a -> AlbumCard(a, Modifier.fillMaxWidth()) { actions.openGroup(GroupType.ALBUM, a.key) } }
    }
}

@Composable
fun AlbumCard(album: MediaGroup, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        AlbumArt(album.cover, Modifier.fillMaxWidth().aspectRatio(1f), sizePx = 400, corner = 10.dp)
        Spacer(Modifier.height(6.dp))
        Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        Text(
            album.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistsTab(playlists: List<Playlist>, library: Library, actions: UiActions) {
    var showCreate by remember { mutableStateOf(false) }
    var showMeta by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    val vm = actions.vm

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Neue Playlist", maxLines = 1)
                }
                OutlinedButton(onClick = { showMeta = true }, enabled = library.songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Aus Metadaten", maxLines = 1)
                }
            }
        }
        if (playlists.isEmpty()) {
            item {
                Text(
                    "Noch keine eigenen Playlists.\n\n" +
                        "• „Neue Playlist“ erstellt eine leere Liste – Titel fügst du über das ⋮-Menü hinzu.\n" +
                        "• „Aus Metadaten“ baut eine Playlist aus Künstlern, Alben oder Genres.\n" +
                        "• Auf jeder Künstler-, Album- oder Genre-Seite kannst du „Als Playlist speichern“ wählen.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(playlists, key = { it.id }) { p ->
            val songs = p.songIds.mapNotNull { library.songsById[it] }
            var menuOpen by remember { mutableStateOf(false) }
            ListItem(
                modifier = Modifier.clickable { actions.openPlaylist(p.id) },
                headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text("${songs.size} Titel · ${formatTime(songs.sumOf { it.durationMs })}")
                },
                leadingContent = { AlbumArt(songs.firstOrNull(), Modifier.size(52.dp)) },
                trailingContent = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Abspielen") }, enabled = songs.isNotEmpty(),
                                onClick = { menuOpen = false; vm.play(songs) })
                            DropdownMenuItem(text = { Text("Zufällig abspielen") }, enabled = songs.isNotEmpty(),
                                onClick = { menuOpen = false; vm.shuffle(songs) })
                            DropdownMenuItem(text = { Text("Umbenennen") }, onClick = { menuOpen = false; renaming = p })
                            DropdownMenuItem(text = { Text("Löschen") }, onClick = { menuOpen = false; deleting = p })
                        }
                    }
                },
            )
        }
    }

    if (showCreate) {
        NameDialog("Neue Playlist", confirmLabel = "Erstellen", onDismiss = { showCreate = false }) {
            vm.createPlaylist(it); showCreate = false
        }
    }
    if (showMeta) {
        MetadataPlaylistDialog(library, onDismiss = { showMeta = false }) { name, songs ->
            vm.createPlaylist(name, songs); showMeta = false
        }
    }
    renaming?.let { p ->
        NameDialog("Playlist umbenennen", initial = p.name, onDismiss = { renaming = null }) {
            vm.renamePlaylist(p.id, it); renaming = null
        }
    }
    deleting?.let { p ->
        ConfirmDialog(
            "Playlist löschen?", "„${p.name}“ wird gelöscht. Die Musikdateien bleiben erhalten.", "Löschen",
            onDismiss = { deleting = null },
        ) { vm.deletePlaylist(p.id); deleting = null }
    }
}

