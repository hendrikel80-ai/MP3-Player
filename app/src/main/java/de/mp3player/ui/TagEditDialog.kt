package de.mp3player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mp3player.data.MusicRepository
import de.mp3player.data.Song
import de.mp3player.data.tags.TagChanges

/** Ein Feld im Bearbeiten-Dialog. Bei mehreren Titeln mit unterschiedlichen Werten ist es „gemischt“. */
private class Field(val initial: String, val mixed: Boolean) {
    var value by mutableStateOf(initial)

    /** null = nicht ändern. */
    fun change(): String? {
        val v = value.trim()
        return when {
            v == initial -> null
            mixed && v.isEmpty() -> null
            else -> v
        }
    }
}

private fun field(songs: List<Song>, selector: (Song) -> String): Field {
    val values = songs.map(selector).distinct()
    return Field(values.singleOrNull() ?: "", values.size > 1)
}

/**
 * Bearbeitet die Metadaten (ID3-Tags) eines oder mehrerer MP3-Titel.
 * Bei mehreren Titeln werden nur ausgefüllte/geänderte Felder übernommen.
 */
@Composable
fun TagEditDialog(songs: List<Song>, genres: List<String>, onDismiss: () -> Unit, onSave: (TagChanges) -> Unit) {
    val mp3 = remember(songs) { songs.filter { it.isMp3 } }
    val skipped = songs.size - mp3.size
    val single = mp3.size == 1

    val title = remember(mp3) { field(mp3) { it.title } }
    val artist = remember(mp3) { field(mp3) { it.artist.takeUnless { a -> a == MusicRepository.UNKNOWN_ARTIST }.orEmpty() } }
    val album = remember(mp3) { field(mp3) { it.album.takeUnless { a -> a == MusicRepository.UNKNOWN_ALBUM }.orEmpty() } }
    val genre = remember(mp3) { field(mp3) { it.genre.takeUnless { g -> g == MusicRepository.UNKNOWN_GENRE }.orEmpty() } }
    val year = remember(mp3) { field(mp3) { if (it.year > 0) it.year.toString() else "" } }
    val track = remember(mp3) { field(mp3) { if (it.trackNo > 0) it.trackNo.toString() else "" } }

    fun changes() = TagChanges(
        title = if (single) title.change() else null,
        artist = artist.change(),
        album = album.change(),
        genre = genre.change(),
        year = year.change(),
        track = if (single) track.change() else null,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (single) "Metadaten bearbeiten" else "${mp3.size} Titel bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (mp3.isEmpty()) {
                    Text("Nur MP3-Dateien können bearbeitet werden.")
                } else {
                    TagFields(mp3, single, skipped, genres, title, artist, album, genre, year, track)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = mp3.isNotEmpty(), onClick = { onSave(changes()) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun TagFields(
    mp3: List<Song>,
    single: Boolean,
    skipped: Int,
    genres: List<String>,
    title: Field,
    artist: Field,
    album: Field,
    genre: Field,
    year: Field,
    track: Field,
) {
    Column {
                if (single) {
                    Text(
                        mp3[0].fileName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    TagField("Titel", title)
                } else {
                    Text(
                        "Leere Felder mit „verschieden“ bleiben bei jedem Titel unverändert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TagField("Künstler", artist)
                TagField("Album", album)
                TagField("Genre", genre)
                if (genres.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(genres) { g -> SuggestionChip(onClick = { genre.value = g }, label = { Text(g) }) }
                    }
                }
                Row {
                    TagField("Jahr", year, Modifier.weight(1f), numeric = true)
                    if (single) {
                        Spacer(Modifier.width(8.dp))
                        TagField("Track-Nr.", track, Modifier.weight(1f), numeric = true)
                    }
                }
                if (skipped > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$skipped Titel sind keine MP3-Dateien und werden übersprungen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
    }
}

@Composable
private fun TagField(label: String, field: Field, modifier: Modifier = Modifier.fillMaxWidth(), numeric: Boolean = false) {
    OutlinedTextField(
        value = field.value,
        onValueChange = { v -> field.value = if (numeric) v.filter { it.isDigit() || it == '/' }.take(7) else v },
        label = { Text(label) },
        placeholder = { if (field.mixed) Text("verschieden") },
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier,
    )
}
