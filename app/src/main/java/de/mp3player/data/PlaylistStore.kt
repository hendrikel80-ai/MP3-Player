package de.mp3player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long,
)

/** Speichert eigene Playlists als JSON-Datei im privaten App-Speicher. */
class PlaylistStore(context: Context) {

    private val file = File(context.filesDir, "playlists.json")
    private val mutex = Mutex()
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    suspend fun load() = mutex.withLock {
        _playlists.value = withContext(Dispatchers.IO) { read() }
    }

    suspend fun create(name: String, songIds: List<Long> = emptyList()): Long {
        var newId = 0L
        update { list ->
            newId = (list.maxOfOrNull { it.id } ?: 0L) + 1
            list + Playlist(newId, name.trim(), songIds.distinct(), System.currentTimeMillis())
        }
        return newId
    }

    suspend fun rename(id: Long, name: String) = edit(id) { it.copy(name = name.trim()) }

    suspend fun delete(id: Long) = update { list -> list.filterNot { it.id == id } }

    /** Fügt Titel hinzu (ohne Duplikate). Gibt die Anzahl neu hinzugefügter Titel zurück. */
    suspend fun addSongs(id: Long, songIds: List<Long>): Int {
        var added = 0
        edit(id) { p ->
            val existing = p.songIds.toSet()
            val fresh = songIds.distinct().filterNot { it in existing }
            added = fresh.size
            p.copy(songIds = p.songIds + fresh)
        }
        return added
    }

    suspend fun removeSong(id: Long, songId: Long) = edit(id) { p -> p.copy(songIds = p.songIds - songId) }

    suspend fun moveSong(id: Long, songId: Long, delta: Int) = edit(id) { p ->
        val list = p.songIds.toMutableList()
        val from = list.indexOf(songId)
        val to = from + delta
        if (from < 0 || to !in list.indices) return@edit p
        list.removeAt(from)
        list.add(to, songId)
        p.copy(songIds = list)
    }

    private suspend fun edit(id: Long, transform: (Playlist) -> Playlist) = update { list ->
        list.map { if (it.id == id) transform(it) else it }
    }

    private suspend fun update(transform: (List<Playlist>) -> List<Playlist>) = mutex.withLock {
        val updated = transform(_playlists.value)
        _playlists.value = updated
        withContext(Dispatchers.IO) { write(updated) }
    }

    private fun read(): List<Playlist> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val songs = o.getJSONArray("songs")
                Playlist(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    songIds = (0 until songs.length()).map { songs.getLong(it) },
                    createdAt = o.optLong("createdAt"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("createdAt", p.createdAt)
                    .put("songs", JSONArray(p.songIds)),
            )
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }
}
