package de.mp3player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import de.mp3player.data.GroupType
import de.mp3player.data.Library
import de.mp3player.data.Song
import de.mp3player.playback.PlaybackService
import de.mp3player.playback.PlayerState
import de.mp3player.ui.theme.EyebrowStyle
import de.mp3player.ui.theme.Studio

@Composable
fun PlayerScreen(actions: UiActions) {
    val vm = actions.vm
    val state by vm.playerState.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val song = state.currentId?.let { library.songsById[it] }
    var showQueue by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.back) { Icon(Icons.Outlined.KeyboardArrowDown, "Schließen") }
                Text(
                    if (showQueue) "Warteschlange" else "Läuft gerade",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = EyebrowStyle,
                )
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        Icons.Outlined.QueueMusic, "Warteschlange",
                        tint = if (showQueue) Studio.Gold else Studio.Text,
                    )
                }
            }

            if (song == null) {
                Box(Modifier.weight(1f)) { EmptyState("Gerade läuft nichts.") }
            } else {
                PlayerBody(song, state, library, showQueue, actions)
            }
        }
    }
}

@Composable
private fun ColumnScope.PlayerBody(
    song: Song,
    state: PlayerState,
    library: Library,
    showQueue: Boolean,
    actions: UiActions,
) {
    val vm = actions.vm
    var dragging by remember { mutableStateOf<Float?>(null) }
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (showQueue) {
                    QueueList(state, library, actions)
                } else {
                    AlbumArt(song, Modifier.fillMaxWidth().aspectRatio(1f), sizePx = 900, corner = 16.dp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = Studio.Gold,
                        modifier = Modifier.clickable { actions.openGroup(GroupType.ARTIST, song.artistKey) },
                    )
                    Text(
                        song.album, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (song.isMp3) {
                    IconButton(onClick = { actions.editTags(listOf(song)) }) {
                        Icon(Icons.Outlined.Edit, "Metadaten bearbeiten")
                    }
                }
                IconButton(onClick = { actions.addToPlaylist(listOf(song)) }) {
                    Icon(Icons.Outlined.PlaylistAdd, "Zu Playlist hinzufügen")
                }
            }

            Spacer(Modifier.height(8.dp))
            val duration = state.durationMs.coerceAtLeast(1L).toFloat()
            GoldRailSlider(
                value = (dragging ?: state.positionMs.toFloat()).coerceIn(0f, duration),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { vm.seekTo(it.toLong()) }
                    dragging = null
                },
                valueRange = 0f..duration,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(dragging?.toLong() ?: state.positionMs), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val active = Studio.Gold
                val inactive = Studio.TextDim
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(Icons.Outlined.Shuffle, "Zufallswiedergabe", tint = if (state.shuffle) active else inactive)
                }
                IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.SkipPrevious, "Vorheriger Titel", Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = vm::playPause,
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(18.dp, CircleShape, ambientColor = Studio.Sunburst, spotColor = Studio.Sunburst),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Studio.Sunburst,
                        contentColor = Studio.OnAccent,
                    ),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (state.isPlaying) "Pause" else "Abspielen",
                        Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.SkipNext, "Nächster Titel", Modifier.size(36.dp))
                }
                IconButton(onClick = vm::cycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                        "Wiederholen",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) active else inactive,
                    )
                }
            }
            SpeedSelector(state.speed, vm::setSpeed, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
}

/** Regler im Stil „Gold Rail“: dünne Schiene, runder Gold-Griff mit leichtem Glühen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoldRailSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    val colors = SliderDefaults.colors(
        thumbColor = Studio.Gold,
        activeTrackColor = Studio.Gold,
        inactiveTrackColor = Studio.Border,
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        colors = colors,
        thumb = {
            Box(
                Modifier
                    .size(16.dp)
                    .shadow(6.dp, CircleShape, ambientColor = Studio.Gold, spotColor = Studio.Gold)
                    .background(Studio.Gold, CircleShape)
                    .border(2.dp, Studio.Bg, CircleShape),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                modifier = Modifier.height(4.dp),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
    )
}

/** Knopf „1×“ mit Auswahl der Abspielgeschwindigkeit. */
@Composable
private fun SpeedSelector(speed: Float, onSelect: (Float) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val changed = speed != 1f
    Box(modifier) {
        TextButton(onClick = { open = true }) {
            Icon(
                Icons.Outlined.Speed, contentDescription = null,
                tint = if (changed) Studio.Gold else Studio.TextDim,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Geschwindigkeit ${formatSpeed(speed)}",
                color = if (changed) Studio.Gold else Studio.TextDim,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PlaybackService.SPEEDS.forEach { s ->
                DropdownMenuItem(
                    text = { Text(formatSpeed(s)) },
                    trailingIcon = { if (s == speed) Icon(Icons.Outlined.Check, contentDescription = "Ausgewählt") },
                    onClick = { open = false; onSelect(s) },
                )
            }
        }
    }
}

fun formatSpeed(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().trimEnd('0')
    return text.replace('.', ',') + "×"
}

@Composable
private fun QueueList(state: PlayerState, library: Library, actions: UiActions) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.queueIndex.coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        itemsIndexed(state.queueIds) { i, id ->
            val s = library.songsById[id]
            val current = i == state.queueIndex
            ListItem(
                modifier = Modifier.clickable { actions.vm.skipToQueueIndex(i) },
                colors = if (current) {
                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    ListItemDefaults.colors()
                },
                headlineContent = { Text(s?.title ?: "Unbekannter Titel", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(s?.artist.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { AlbumArt(s, Modifier.size(40.dp)) },
                trailingContent = {
                    if (!current) {
                        IconButton(onClick = { actions.vm.removeFromQueue(i) }) { Icon(Icons.Outlined.Close, "Entfernen") }
                    }
                },
            )
        }
    }
}
