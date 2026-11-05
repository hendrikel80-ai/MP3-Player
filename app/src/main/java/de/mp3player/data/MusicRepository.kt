package de.mp3player.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Liest alle Musikdateien inkl. Metadaten aus der Android-Medienbibliothek. */
object MusicRepository {

    const val UNKNOWN_ARTIST = "Unbekannter Künstler"
    const val UNKNOWN_ALBUM = "Unbekanntes Album"
    const val UNKNOWN_GENRE = "Unbekanntes Genre"

    suspend fun loadSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val hasGenreColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.MIME_TYPE)
            if (hasGenreColumn) add(MediaStore.Audio.Media.GENRE)
        }.toTypedArray()

        val legacyGenres = if (hasGenreColumn) emptyMap() else loadLegacyGenres(resolver)
        val songs = ArrayList<Song>()

        resolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
        )?.use { cur ->
            val iId = cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDuration = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iTrack = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iYear = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val iName = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iMime = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val iGenre = if (hasGenreColumn) cur.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1

            while (cur.moveToNext()) {
                val id = cur.getLong(iId)
                val fileName = cur.getString(iName).orEmpty()
                val genre = if (iGenre >= 0) cur.getString(iGenre).clean() else legacyGenres[id]
                songs += Song(
                    id = id,
                    title = cur.getString(iTitle).clean() ?: fileName.substringBeforeLast('.'),
                    artist = cur.getString(iArtist).clean() ?: UNKNOWN_ARTIST,
                    album = cur.getString(iAlbum).clean() ?: UNKNOWN_ALBUM,
                    albumId = cur.getLong(iAlbumId),
                    genre = genre.clean() ?: UNKNOWN_GENRE,
                    durationMs = cur.getLong(iDuration),
                    track = cur.getInt(iTrack),
                    year = cur.getInt(iYear),
                    uri = ContentUris.withAppendedId(collection, id),
                    mimeType = cur.getString(iMime).orEmpty(),
                    fileName = fileName,
                )
            }
        }
        songs
    }

    /** Vor Android 11 gibt es keine Genre-Spalte – dort über die Genre-Tabelle zuordnen. */
    @Suppress("DEPRECATION")
    private fun loadLegacyGenres(resolver: ContentResolver): Map<Long, String> {
        val result = HashMap<Long, String>()
        try {
            resolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null,
            )?.use { genres ->
                while (genres.moveToNext()) {
                    val genreId = genres.getLong(0)
                    val name = genres.getString(1).clean() ?: continue
                    resolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null,
                    )?.use { members ->
                        while (members.moveToNext()) result[members.getLong(0)] = name
                    }
                }
            }
        } catch (_: Exception) {
            // Genres sind optional – ohne sie funktioniert der Rest trotzdem.
        }
        return result
    }

    private fun String?.clean(): String? {
        val s = this?.trim()
        return if (s.isNullOrEmpty() || s == MediaStore.UNKNOWN_STRING) null else s
    }
}
