@file:OptIn(ExperimentalMaterial3Api::class)

package de.mp3player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mp3player.data.GroupType
import de.mp3player.data.Library
import de.mp3player.data.MediaGroup
import de.mp3player.data.Playlist
import de.mp3player.data.Song
import de.mp3player.playback.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bündelt Navigation und Aktionen, die viele Bildschirme brauchen. */
class UiActions(
    val vm: MainViewModel,
    val openGroup: (GroupType, String) -> Unit,
    val openPlaylist: (Long) -> Unit,
    val openPlayer: () -> Unit,
    val addToPlaylist: (List<Song>) -> Unit,
    val editTags: (List<Song>) -> Unit,
    val back: () -> Unit,
)

// ---------------------------------------------------------------- Cover

object ArtLoader {
    private class Entry(val bitmap: ImageBitmap?, val bytes: Int)

    private val cache = object : LruCache<String, Entry>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Entry) = maxOf(value.bytes, 1)
    }

    private fun key(song: Song, size: Int) = "${song.albumId}:$size"

    fun cached(song: Song, size: Int): ImageBitmap? = cache.get(key(song, size))?.bitmap

    suspend fun load(context: Context, song: Song, size: Int): ImageBitmap? {
        cache.get(key(song, size))?.let { return it.bitmap }
        val bmp = withContext(Dispatchers.IO) { decode(context, song, size) }
        val image = bmp?.asImageBitmap()
        cache.put(key(song, size), Entry(image, bmp?.byteCount ?: 0))
        return image
    }

    private fun decode(context: Context, song: Song, size: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(song.uri, Size(size, size), null)
        } else {
            context.contentResolver.openInputStream(song.artUri)?.use { BitmapFactory.decodeStream(it) }
                ?.let { b ->
                    if (b.width > size) Bitmap.createScaledBitmap(b, size, size * b.height / b.width, true) else b
                }
        }
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun AlbumArt(song: Song?, modifier: Modifier = Modifier, sizePx: Int = 160, corner: Dp = 6.dp) {
    val context = LocalContext.current
    val art by produceState(
        initialValue = song?.let { ArtLoader.cached(it, sizePx) },
        song?.albumId, song?.id, sizePx,
    ) {
        value = song?.let { ArtLoader.load(context, it, sizePx) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = art
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Filled.MusicNote, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxSize(0.5f),
            )
        }
    }
}

// ---------------------------------------------------------------- Listen

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    subtitle: String = "${song.artist} · ${song.album}",
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
            )
        },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { AlbumArt(song, Modifier.size(48.dp)) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Mehr")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu { menuOpen = false }
                }
            }
        },
    )
}

@Composable
fun SongMenuItems(songs: List<Song>, actions: UiActions, close: () -> Unit, showNavigation: Boolean = true) {
    DropdownMenuItem(text = { Text("Als Nächstes abspielen") }, onClick = { actions.vm.playNext(songs); close() })
    DropdownMenuItem(text = { Text("Zur Warteschlange hinzufügen") }, onClick = { actions.vm.addToQueue(songs); close() })
    DropdownMenuItem(text = { Text("Zu Playlist hinzufügen…") }, onClick = { actions.addToPlaylist(songs); close() })
    if (songs.any { it.isMp3 }) {
        DropdownMenuItem(text = { Text("Metadaten bearbeiten…") }, onClick = { close(); actions.editTags(songs) })
    }
    val song = songs.singleOrNull()
    if (showNavigation && song != null) {
        DropdownMenuItem(
            text = { Text("Zum Künstler") },
            onClick = { close(); actions.openGroup(GroupType.ARTIST, song.artistKey) },
        )
        DropdownMenuItem(
            text = { Text("Zum Album") },
            onClick = { close(); actions.openGroup(GroupType.ALBUM, song.albumKey) },
        )
        DropdownMenuItem(
            text = { Text("Zum Genre") },
            onClick = { close(); actions.openGroup(GroupType.GENRE, song.genreKey) },
        )
    }
}

@Composable
fun GroupRow(group: MediaGroup, onClick: () -> Unit, round: Boolean = false) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(group.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(group.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { AlbumArt(group.cover, Modifier.size(52.dp), corner = if (round) 26.dp else 6.dp) },
    )
}

/** Kopfbereich für Detailseiten: Cover, Titel und Abspiel-Knöpfe. */
@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    cover: Song?,
    songs: List<Song>,
    actions: UiActions,
    extra: @Composable () -> Unit = {},
) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(cover, Modifier.size(112.dp), sizePx = 400, corner = 10.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { actions.vm.play(songs) }, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Abspielen")
            }
            FilledTonalButton(onClick = { actions.vm.shuffle(songs) }, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Shuffle, null)
                Spacer(Modifier.width(6.dp))
                Text("Zufall")
            }
        }
        extra()
    }
}

// ---------------------------------------------------------------- Mini-Player

@Composable
fun MiniPlayer(song: Song, state: PlayerState, actions: UiActions) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = actions.openPlayer),
    ) {
        Column {
            LinearProgressIndicator(
                progress = { if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(song, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (state.speed != 1f) "${formatSpeed(state.speed)} · ${song.artist}" else song.artist,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = actions.vm::playPause) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Abspielen",
                    )
                }
                IconButton(onClick = actions.vm::next) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Nächster Titel")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Dialoge

@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String = "Speichern",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun AddToPlaylistDialog(
    songs: List<Song>,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (songs.size == 1) "Zu Playlist hinzufügen" else "${songs.size} Titel zu Playlist hinzufügen") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("Neue Playlist") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(enabled = newName.isNotBlank(), onClick = { onCreate(newName.trim()) }) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = "Erstellen")
                    }
                }
                if (playlists.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(playlists, key = { it.id }) { p ->
                            ListItem(
                                modifier = Modifier.clickable { onPick(p.id) },
                                headlineContent = { Text(p.name) },
                                supportingContent = { Text("${p.songIds.size} Titel") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Erstellt eine Playlist aus einem oder mehreren Künstlern, Alben oder Genres. */
@Composable
fun MetadataPlaylistDialog(library: Library, onDismiss: () -> Unit, onCreate: (String, List<Song>) -> Unit) {
    var type by remember { mutableStateOf(GroupType.ARTIST) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var filter by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }

    val groups = library.groups(type)
    val visible = remember(groups, filter) {
        if (filter.isBlank()) groups else groups.filter { it.title.contains(filter.trim(), ignoreCase = true) }
    }
    val chosen = groups.filter { it.key in selected }
    val songs = chosen.flatMap { it.songs }.distinctBy { it.id }
    val suggested = chosen.take(3).joinToString(", ") { it.title } + if (chosen.size > 3) " …" else ""
    val finalName = if (nameEdited) name else suggested

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlist aus Metadaten") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupType.entries.forEach { t ->
                        FilterChip(
                            selected = t == type,
                            onClick = { type = t; selected = emptySet(); filter = "" },
                            label = { Text(t.plural) },
                        )
                    }
                }
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it },
                    label = { Text("${type.plural} durchsuchen") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 240.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(visible, key = { it.key }) { g ->
                        val checked = g.key in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (checked) selected - g.key else selected + g.key }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Column {
                                Text(g.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    g.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = finalName, onValueChange = { name = it; nameEdited = true },
                    label = { Text("Name der Playlist") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text("${songs.size} Titel ausgewählt", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = songs.isNotEmpty(), onClick = { onCreate(finalName.ifBlank { "Neue Playlist" }, songs) }) {
                Text("Erstellen")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun EmptyState(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.MusicNote, null, Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

