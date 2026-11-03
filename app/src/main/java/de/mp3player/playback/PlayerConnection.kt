package de.mp3player.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import de.mp3player.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerState(
    val currentId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueIds: List<Long> = emptyList(),
    val queueIndex: Int = 0,
)

/** Verbindet die Oberfläche mit dem [PlaybackService]. */
class PlayerConnection(context: Context) {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private val future: ListenableFuture<MediaController>

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                pending.forEach { it(c) }
                pending.clear()
                publish()
            } catch (e: Exception) {
                Log.e("PlayerConnection", "Verbindung zum Player fehlgeschlagen", e)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending += block
    }

    private fun publish() {
        val c = controller ?: return
        _state.value = PlayerState(
            currentId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition,
            durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queueIds = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L },
            queueIndex = c.currentMediaItemIndex,
        )
    }

    fun refreshPosition() {
        val c = controller ?: return
        _state.update {
            it.copy(
                positionMs = c.currentPosition,
                durationMs = c.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L,
            )
        }
    }

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle) songs.indices.random() else startIndex.coerceIn(songs.indices)
            c.setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) {
            play(songs)
        } else {
            c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
        }
    }

    fun addToQueue(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) play(songs) else c.addMediaItems(songs.map { it.toMediaItem() })
    }

    fun playPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun skipToQueueIndex(index: Int) = withController { c ->
        c.seekToDefaultPosition(index)
        c.play()
    }
    fun removeFromQueue(index: Int) = withController { it.removeMediaItem(index) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun release() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(future)
        controller = null
    }
}

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setGenre(genre)
            .setArtworkUri(artUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build(),
    )
    .build()
