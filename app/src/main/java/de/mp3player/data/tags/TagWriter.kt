package de.mp3player.data.tags

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import de.mp3player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/** Schreibt geänderte Metadaten in die MP3-Dateien auf dem Gerät. */
object TagWriter {

    private const val MAX_FILE_BYTES = 150L * 1024 * 1024

    sealed interface Outcome {
        data class Done(val written: Int, val skipped: Int, val failed: List<Pair<Song, String>>) : Outcome

        /** Android 10: Für eine Datei muss der Nutzer den Schreibzugriff einzeln erlauben. */
        data class NeedsPermission(val intentSender: IntentSender, val remaining: List<Song>, val written: Int) : Outcome
    }

    suspend fun write(context: Context, songs: List<Song>, changes: TagChanges): Outcome = withContext(Dispatchers.IO) {
        var written = 0
        var skipped = 0
        val failed = mutableListOf<Pair<Song, String>>()
        for ((index, song) in songs.withIndex()) {
            if (!song.isMp3) {
                skipped++
                continue
            }
            try {
                writeOne(context, song, changes)
                written++
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    return@withContext Outcome.NeedsPermission(
                        e.userAction.actionIntent.intentSender, songs.drop(index), written,
                    )
                }
                failed += song to "Kein Schreibzugriff"
            } catch (e: Id3Exception) {
                failed += song to (e.message ?: "Ungültiger Tag")
            } catch (e: Exception) {
                failed += song to (e.message ?: e.javaClass.simpleName)
            } catch (e: OutOfMemoryError) {
                failed += song to "Datei zu groß"
            }
        }
        Outcome.Done(written, skipped, failed)
    }

    private fun writeOne(context: Context, song: Song, changes: TagChanges) {
        val resolver = context.contentResolver
        val original = resolver.openFileDescriptor(song.uri, "r")?.use { pfd ->
            if (pfd.statSize > MAX_FILE_BYTES) throw IllegalStateException("Datei zu groß")
            ParcelFileDescriptor.AutoCloseInputStream(pfd.dup()).use { it.readBytes() }
        } ?: throw IllegalStateException("Datei nicht lesbar")

        // Erst die komplette neue Datei im Speicher bauen – bei einem Fehler bleibt das Original unberührt.
        val updated = Id3.write(original, changes)

        val pfd = resolver.openFileDescriptor(song.uri, "rw") ?: throw IllegalStateException("Datei nicht beschreibbar")
        try {
            overwrite(pfd, updated)
        } catch (e: Exception) {
            // Notfall: Original zurückschreiben
            runCatching { resolver.openFileDescriptor(song.uri, "rw")?.let { overwrite(it, original) } }
            throw e
        }
        scan(context, song)
    }

    private fun overwrite(pfd: ParcelFileDescriptor, bytes: ByteArray) {
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
            val channel = out.channel
            channel.truncate(0)
            channel.position(0)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    /** Teilt Android mit, dass sich die Datei geändert hat, damit die Musikbibliothek aktualisiert wird. */
    @Suppress("DEPRECATION")
    private fun scan(context: Context, song: Song) {
        val path = runCatching {
            context.contentResolver.query(song.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: return
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("audio/mpeg"), null)
    }
}
