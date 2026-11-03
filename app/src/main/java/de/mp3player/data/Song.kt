package de.mp3player.data

import android.content.ContentUris
import android.net.Uri
import java.text.Collator

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val genre: String,
    val durationMs: Long,
    val track: Int,
    val year: Int,
    val uri: Uri,
) {
    val disc: Int get() = track / 1000
    val trackNo: Int get() = track % 1000
    val artUri: Uri get() = ContentUris.withAppendedId(ALBUM_ART_URI, albumId)

    val artistKey: String get() = artist.trim().lowercase()
    val albumKey: String get() = albumId.toString()
    val genreKey: String get() = genre.trim().lowercase()

    companion object {
        private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}

enum class GroupType(val label: String, val plural: String) {
    ARTIST("Künstler", "Künstler"),
    ALBUM("Album", "Alben"),
    GENRE("Genre", "Genres");

    fun keyOf(song: Song): String = when (this) {
        ARTIST -> song.artistKey
        ALBUM -> song.albumKey
        GENRE -> song.genreKey
    }
}

/** Eine automatisch aus den Metadaten erzeugte Sammlung (Künstler, Album oder Genre). */
data class MediaGroup(
    val type: GroupType,
    val key: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
) {
    val cover: Song? get() = songs.firstOrNull()
}

data class Library(
    val songs: List<Song> = emptyList(),
    val artists: List<MediaGroup> = emptyList(),
    val albums: List<MediaGroup> = emptyList(),
    val genres: List<MediaGroup> = emptyList(),
) {
    val songsById: Map<Long, Song> by lazy { songs.associateBy { it.id } }

    fun groups(type: GroupType): List<MediaGroup> = when (type) {
        GroupType.ARTIST -> artists
        GroupType.ALBUM -> albums
        GroupType.GENRE -> genres
    }

    fun group(type: GroupType, key: String): MediaGroup? = groups(type).firstOrNull { it.key == key }

    companion object {
        fun build(all: List<Song>): Library {
            val c = Collator.getInstance().apply { strength = Collator.PRIMARY }
            val byTitle = Comparator<Song> { a, b -> c.compare(a.title, b.title) }
            val byTrack = Comparator<Song> { a, b ->
                compareValuesBy(a, b, { it.disc }, { it.trackNo }).takeIf { it != 0 } ?: c.compare(a.title, b.title)
            }
            val byAlbumTrack = Comparator<Song> { a, b ->
                c.compare(a.album, b.album).takeIf { it != 0 } ?: byTrack.compare(a, b)
            }
            val byArtistAlbumTrack = Comparator<Song> { a, b ->
                c.compare(a.artist, b.artist).takeIf { it != 0 } ?: byAlbumTrack.compare(a, b)
            }
            val groupOrder = Comparator<MediaGroup> { a, b -> c.compare(a.title, b.title) }

            val artists = all.groupBy { it.artistKey }.map { (key, list) ->
                val songs = list.sortedWith(byAlbumTrack)
                val albumCount = songs.map { it.albumId }.distinct().size
                MediaGroup(
                    GroupType.ARTIST, key, songs.first().artist,
                    "${count(albumCount, "Album", "Alben")} · ${count(songs.size, "Titel", "Titel")}",
                    songs,
                )
            }.sortedWith(groupOrder)

            val albums = all.groupBy { it.albumKey }.map { (key, list) ->
                val songs = list.sortedWith(byTrack)
                val artistNames = songs.map { it.artist }.distinct()
                val artist = if (artistNames.size == 1) artistNames.first() else "Verschiedene Künstler"
                val year = songs.maxOf { it.year }
                MediaGroup(
                    GroupType.ALBUM, key, songs.first().album,
                    if (year > 0) "$artist · $year" else artist,
                    songs,
                )
            }.sortedWith(groupOrder)

            val genres = all.groupBy { it.genreKey }.map { (key, list) ->
                val songs = list.sortedWith(byArtistAlbumTrack)
                val artistCount = songs.map { it.artistKey }.distinct().size
                MediaGroup(
                    GroupType.GENRE, key, songs.first().genre,
                    "${count(artistCount, "Künstler", "Künstler")} · ${count(songs.size, "Titel", "Titel")}",
                    songs,
                )
            }.sortedWith(groupOrder)

            return Library(all.sortedWith(byTitle), artists, albums, genres)
        }

        private fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
    }
}
