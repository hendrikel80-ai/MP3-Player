package de.mp3player.data.tags

import java.io.ByteArrayOutputStream

/**
 * Änderungen an den Metadaten. `null` = unverändert lassen, leerer Text = Feld entfernen.
 */
data class TagChanges(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val track: String? = null,
) {
    val isEmpty: Boolean get() = listOf(title, artist, album, genre, year, track).all { it == null }
}

class Id3Exception(message: String) : Exception(message)

/**
 * Kleiner, abhängigkeitsfreier ID3-Leser/-Schreiber für MP3-Dateien.
 *
 * - Liest ID3v2.2, v2.3 und v2.4.
 * - Ändert nur die gewünschten Textfelder; alle anderen Frames (z. B. Cover, Kommentare,
 *   Songtexte) werden unverändert übernommen.
 * - Ein vorhandener ID3v1-Tag am Dateiende wird mit aktualisiert.
 */
object Id3 {

    class Frame(val id: String, val flags: Int, val data: ByteArray)

    class Tag(val major: Int, val frames: List<Frame>, /** Länge des Tags am Dateianfang in Bytes. */ val length: Int)

    private const val PADDING = 1024

    // ------------------------------------------------------------------ Lesen

    fun parse(b: ByteArray): Tag? {
        if (b.size < 10 || b[0] != 'I'.code.toByte() || b[1] != 'D'.code.toByte() || b[2] != '3'.code.toByte()) return null
        val major = b[3].u
        if (major !in 2..4) throw Id3Exception("Unbekannte ID3-Version 2.$major")
        val flags = b[5].u
        val size = syncsafe(b, 6)
        var length = 10 + size
        if (major == 4 && flags and 0x10 != 0) length += 10 // Footer
        if (10 + size > b.size) throw Id3Exception("Der ID3-Tag ist beschädigt")

        var body = b.copyOfRange(10, 10 + size)
        if (major <= 3 && flags and 0x80 != 0) body = deUnsync(body)

        var pos = 0
        if (major >= 3 && flags and 0x40 != 0 && body.size >= 4) {
            pos = if (major == 3) 4 + be32(body, 0) else syncsafe(body, 0)
        }

        val idLen = if (major == 2) 3 else 4
        val headerLen = if (major == 2) 6 else 10
        val frames = ArrayList<Frame>()
        while (pos + headerLen <= body.size) {
            if (body[pos].toInt() == 0) break // Padding
            val id = String(body, pos, idLen, Charsets.ISO_8859_1)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) break
            val frameSize = when (major) {
                2 -> (body[pos + 3].u shl 16) or (body[pos + 4].u shl 8) or body[pos + 5].u
                3 -> be32(body, pos + 4)
                else -> syncsafe(body, pos + 4)
            }
            val frameFlags = if (major == 2) 0 else (body[pos + 8].u shl 8) or body[pos + 9].u
            val start = pos + headerLen
            if (frameSize < 0 || start + frameSize > body.size) break
            frames += Frame(id, frameFlags, body.copyOfRange(start, start + frameSize))
            pos = start + frameSize
        }
        return Tag(major, frames, length)
    }

    /** Liest alle Textframes (Txxx) als Klartext, z. B. "TIT2" → "Titel". */
    fun readText(b: ByteArray): Map<String, String> {
        val tag = parse(b) ?: return emptyMap()
        val frames = if (tag.major == 2) convertV22(tag.frames) else tag.frames
        val result = LinkedHashMap<String, String>()
        for (f in frames) {
            if (!f.id.startsWith("T") || f.id == "TXXX") continue
            var data = f.data
            if (tag.major == 4) {
                if (f.flags and 0x000C != 0) continue // komprimiert/verschlüsselt
                if (f.flags and 0x0001 != 0 && data.size >= 4) data = data.copyOfRange(4, data.size)
                if (f.flags and 0x0002 != 0) data = deUnsync(data)
            } else if (tag.major == 3 && f.flags and 0x00C0 != 0) {
                continue
            }
            decodeText(data)?.let { result[f.id] = it }
        }
        return result
    }

    // ------------------------------------------------------------------ Schreiben

    /** Gibt die komplette neue Datei (Tag + Audiodaten) zurück. */
    fun write(b: ByteArray, changes: TagChanges): ByteArray {
        val old = parse(b)
        val major = if (old == null || old.major == 2) 3 else old.major
        val frames = when {
            old == null -> mutableListOf()
            old.major == 2 -> convertV22(old.frames).toMutableList()
            else -> old.frames.toMutableList()
        }

        fun set(id: String, value: String?) {
            if (value == null) return
            val v = value.trim()
            val index = frames.indexOfFirst { it.id == id }
            frames.removeAll { it.id == id }
            if (v.isNotEmpty()) {
                val frame = textFrame(id, v, major)
                if (index >= 0) frames.add(index.coerceAtMost(frames.size), frame) else frames.add(frame)
            }
        }
        set("TIT2", changes.title)
        set("TPE1", changes.artist)
        set("TALB", changes.album)
        set("TCON", changes.genre)
        set("TRCK", changes.track)
        if (changes.year != null) {
            frames.removeAll { it.id == "TYER" || it.id == "TDRC" }
            set(if (major == 4) "TDRC" else "TYER", changes.year)
        }

        val body = ByteArrayOutputStream()
        for (f in frames) {
            body.write(f.id.toByteArray(Charsets.ISO_8859_1))
            body.write(if (major == 4) toSyncsafe(f.data.size) else toBe32(f.data.size))
            body.write(f.flags shr 8 and 0xFF)
            body.write(f.flags and 0xFF)
            body.write(f.data)
        }
        val bodyBytes = body.toByteArray()

        val audio = b.copyOfRange(old?.length?.coerceAtMost(b.size) ?: 0, b.size)
        updateId3v1(audio, changes)

        val out = ByteArrayOutputStream(10 + bodyBytes.size + PADDING + audio.size)
        out.write("ID3".toByteArray(Charsets.ISO_8859_1))
        out.write(major)
        out.write(0) // Revision
        out.write(0) // Flags
        out.write(toSyncsafe(bodyBytes.size + PADDING))
        out.write(bodyBytes)
        out.write(ByteArray(PADDING))
        out.write(audio)
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ ID3v1

    private fun updateId3v1(audio: ByteArray, c: TagChanges) {
        if (audio.size < 128) return
        val o = audio.size - 128
        if (audio[o] != 'T'.code.toByte() || audio[o + 1] != 'A'.code.toByte() || audio[o + 2] != 'G'.code.toByte()) return
        fun put(offset: Int, len: Int, value: String?) {
            if (value == null) return
            val bytes = value.trim().toByteArray(Charsets.ISO_8859_1)
            for (i in 0 until len) audio[o + offset + i] = if (i < bytes.size) bytes[i] else 0
        }
        put(3, 30, c.title)
        put(33, 30, c.artist)
        put(63, 30, c.album)
        put(93, 4, c.year)
        if (c.track != null) {
            val n = c.track.trim().takeWhile { it.isDigit() }.toIntOrNull()
            if (n != null && n in 1..255) {
                audio[o + 125] = 0
                audio[o + 126] = n.toByte()
            } else if (audio[o + 125].toInt() == 0) {
                audio[o + 126] = 0
            }
        }
        if (c.genre != null) {
            val idx = ID3V1_GENRES.indexOfFirst { it.equals(c.genre.trim(), ignoreCase = true) }
            audio[o + 127] = if (idx >= 0) idx.toByte() else 0xFF.toByte()
        }
    }

    // ------------------------------------------------------------------ Hilfsfunktionen

    private fun textFrame(id: String, value: String, major: Int): Frame {
        val latin1 = value.all { it.code in 0x20..0xFF }
        val data = when {
            latin1 -> byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1)
            major == 4 -> byteArrayOf(3) + value.toByteArray(Charsets.UTF_8)
            else -> byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + value.toByteArray(Charsets.UTF_16LE)
        }
        return Frame(id, 0, data)
    }

    private fun decodeText(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val raw = data.copyOfRange(1, data.size)
        val text = when (data[0].toInt()) {
            0 -> String(raw, Charsets.ISO_8859_1)
            1 -> String(raw, Charsets.UTF_16)
            2 -> String(raw, Charsets.UTF_16BE)
            3 -> String(raw, Charsets.UTF_8)
            else -> return null
        }
        return text.split('\u0000').map { it.trim() }.firstOrNull { it.isNotEmpty() }
    }

    private val V22_TO_V23 = mapOf(
        "TT2" to "TIT2", "TP1" to "TPE1", "TP2" to "TPE2", "TAL" to "TALB",
        "TCO" to "TCON", "TRK" to "TRCK", "TYE" to "TYER", "TPA" to "TPOS", "TCM" to "TCOM",
    )

    /** ID3v2.2-Frames (3 Zeichen) in v2.3 umwandeln; nicht übertragbare Frames entfallen. */
    private fun convertV22(frames: List<Frame>) = frames.mapNotNull { f -> V22_TO_V23[f.id]?.let { Frame(it, 0, f.data) } }

    private fun deUnsync(d: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(d.size)
        var i = 0
        while (i < d.size) {
            out.write(d[i].toInt())
            if (d[i] == 0xFF.toByte() && i + 1 < d.size && d[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private val Byte.u: Int get() = toInt() and 0xFF
    private fun syncsafe(b: ByteArray, o: Int) =
        (b[o].u and 0x7F shl 21) or (b[o + 1].u and 0x7F shl 14) or (b[o + 2].u and 0x7F shl 7) or (b[o + 3].u and 0x7F)
    private fun be32(b: ByteArray, o: Int) = (b[o].u shl 24) or (b[o + 1].u shl 16) or (b[o + 2].u shl 8) or b[o + 3].u
    private fun toSyncsafe(v: Int) = byteArrayOf(
        (v shr 21 and 0x7F).toByte(), (v shr 14 and 0x7F).toByte(), (v shr 7 and 0x7F).toByte(), (v and 0x7F).toByte(),
    )
    private fun toBe32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    val ID3V1_GENRES = listOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
        "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
        "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk",
        "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
        "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
        "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
        "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes",
        "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock",
    )
}
