package de.mp3player.ui

import android.app.Application
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.mp3player.data.Library
import de.mp3player.data.MusicRepository
import de.mp3player.data.PlaylistStore
import de.mp3player.data.Song
import de.mp3player.data.tags.TagChanges
import de.mp3player.data.tags.TagWriter
import de.mp3player.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _library = MutableStateFlow(Library())
    val library = _library.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _scanned = MutableStateFlow(false)
    val scanned = _scanned.asStateFlow()

    private val playlistStore = PlaylistStore(app)
    val playlists = playlistStore.playlists

    private val player = PlayerConnection(app)
    val playerState = player.state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Systemdialog „Darf die App diese Dateien ändern?“, den die Oberfläche anzeigen muss. */
    private val _writeApproval = MutableStateFlow<IntentSender?>(null)
    val writeApproval = _writeApproval.asStateFlow()
    private var pendingEdit: Pair<List<Song>, TagChanges>? = null
    private var pendingWritten = 0
    private var pendingSkipped = 0

    private var refreshJob: Job? = null
    private var observerRegistered = false

    /** Lädt die Bibliothek automatisch neu, wenn neue MP3s aufs Handy kommen. */
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refreshJob?.cancel()
            refreshJob = viewModelScope.launch {
                delay(2000)
                refresh(silent = true)
            }
        }
    }

    init {
        viewModelScope.launch { playlistStore.load() }
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (playerState.value.isPlaying) player.refreshPosition()
            }
        }
    }

    fun onPermissionGranted() {
        if (!observerRegistered) {
            app.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver,
            )
            observerRegistered = true
        }
        if (!_scanned.value) viewModelScope.launch { refresh() }
    }

    fun rescan() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh(silent: Boolean = false) {
        if (!silent) _loading.value = true
        try {
            val songs = MusicRepository.loadSongs(app)
            _library.value = withContext(Dispatchers.Default) { Library.build(songs) }
            if (!silent && _scanned.value) _messages.trySend("${songs.size} Titel gefunden")
        } catch (e: SecurityException) {
            _messages.trySend("Kein Zugriff auf die Musik – bitte Berechtigung erteilen")
        } catch (e: Exception) {
            _messages.trySend("Fehler beim Lesen der Musik: ${e.message}")
        } finally {
            _loading.value = false
            _scanned.value = true
        }
    }

    // ---- Wiedergabe ----
    fun play(songs: List<Song>, index: Int = 0) = player.play(songs, index)
    fun shuffle(songs: List<Song>) = player.play(songs, shuffle = true)
    fun playNext(songs: List<Song>) {
        player.playNext(songs)
        _messages.trySend(if (songs.size == 1) "Wird als Nächstes gespielt" else "${songs.size} Titel als Nächstes")
    }
    fun addToQueue(songs: List<Song>) {
        player.addToQueue(songs)
        _messages.trySend("${songs.size} Titel zur Warteschlange hinzugefügt")
    }
    fun playPause() = player.playPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()
    fun setSpeed(speed: Float) = player.setSpeed(speed)
    fun skipToQueueIndex(index: Int) = player.skipToQueueIndex(index)
    fun removeFromQueue(index: Int) = player.removeFromQueue(index)

    // ---- Playlists ----
    fun createPlaylist(name: String, songs: List<Song> = emptyList()) {
        viewModelScope.launch {
            playlistStore.create(name, songs.map { it.id })
            _messages.trySend(
                if (songs.isEmpty()) "Playlist „$name“ erstellt"
                else "Playlist „$name“ mit ${songs.size} Titeln erstellt",
            )
        }
    }

    fun addToPlaylist(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch {
            val added = playlistStore.addSongs(playlistId, songs.map { it.id })
            val name = playlists.value.firstOrNull { it.id == playlistId }?.name.orEmpty()
            _messages.trySend(
                when {
                    added == 0 -> "Bereits in „$name“ enthalten"
                    added == 1 -> "1 Titel zu „$name“ hinzugefügt"
                    else -> "$added Titel zu „$name“ hinzugefügt"
                },
            )
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { playlistStore.rename(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { playlistStore.delete(id) }
    }

    fun removeFromPlaylist(id: Long, song: Song) {
        viewModelScope.launch { playlistStore.removeSong(id, song.id) }
    }

    fun moveInPlaylist(id: Long, song: Song, delta: Int) {
        viewModelScope.launch { playlistStore.moveSong(id, song.id, delta) }
    }

    // ---- Metadaten bearbeiten ----
    fun editTags(songs: List<Song>, changes: TagChanges) {
        val mp3 = songs.filter { it.isMp3 }
        if (changes.isEmpty || mp3.isEmpty()) {
            _messages.trySend("Keine Änderungen")
            return
        }
        pendingEdit = mp3 to changes
        pendingWritten = 0
        pendingSkipped = songs.size - mp3.size
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: einmal für alle Dateien um Erlaubnis bitten
            _writeApproval.value = MediaStore.createWriteRequest(app.contentResolver, mp3.map { it.uri }).intentSender
        } else {
            runPendingEdit()
        }
    }

    fun onWriteApprovalResult(granted: Boolean) {
        _writeApproval.value = null
        if (granted) {
            runPendingEdit()
        } else {
            pendingEdit = null
            _messages.trySend("Bearbeiten abgebrochen")
        }
    }

    private fun runPendingEdit() {
        val (songs, changes) = pendingEdit ?: return
        viewModelScope.launch {
            when (val result = TagWriter.write(app, songs, changes)) {
                is TagWriter.Outcome.NeedsPermission -> {
                    pendingWritten += result.written
                    pendingEdit = result.remaining to changes
                    _writeApproval.value = result.intentSender
                }
                is TagWriter.Outcome.Done -> {
                    pendingEdit = null
                    val written = pendingWritten + result.written
                    val skipped = pendingSkipped + result.skipped
                    _messages.trySend(
                        buildString {
                            append(if (written == 1) "Metadaten gespeichert" else "$written Titel aktualisiert")
                            if (skipped > 0) append(" · $skipped übersprungen (keine MP3)")
                            if (result.failed.isNotEmpty()) {
                                append(" · ${result.failed.size} fehlgeschlagen: ${result.failed.first().second}")
                            }
                        },
                    )
                    // Musikbibliothek neu einlesen, sobald Android die Dateien neu gescannt hat
                    delay(1500)
                    refresh(silent = true)
                }
            }
        }
    }

    override fun onCleared() {
        if (observerRegistered) app.contentResolver.unregisterContentObserver(mediaObserver)
        player.release()
    }
}
