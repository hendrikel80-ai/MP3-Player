package de.mp3player.data.tags

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class Id3Test {

    // Simulierte MP3-Audiodaten (Frame-Sync + Inhalt)
    private val audio = ByteArray(4000) { i -> if (i % 417 == 0) 0xFF.toByte() else (i * 7).toByte() }

    private fun syncsafe(v: Int) = byteArrayOf((v shr 21 and 0x7F).toByte(), (v shr 14 and 0x7F).toByte(), (v shr 7 and 0x7F).toByte(), (v and 0x7F).toByte())
    private fun be32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    private fun frame(major: Int, id: String, data: ByteArray, flags: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(if (major == 4) syncsafe(data.size) else be32(data.size))
        out.write(flags shr 8); out.write(flags and 0xFF)
        out.write(data)
        return out.toByteArray()
    }

    private fun text(s: String) = byteArrayOf(0) + s.toByteArray(Charsets.ISO_8859_1)
    private fun utf16(s: String) = byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + s.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)

    private fun tag(major: Int, vararg frames: ByteArray, padding: Int = 100): ByteArray {
        val body = frames.fold(ByteArray(0)) { acc, f -> acc + f } + ByteArray(padding)
        return "ID3".toByteArray() + byteArrayOf(major.toByte(), 0, 0) + syncsafe(body.size) + body
    }

    private val cover = byteArrayOf(0) + "image/jpeg".toByteArray() + byteArrayOf(0, 3, 0) + ByteArray(500) { it.toByte() }

    private fun audioOf(file: ByteArray): ByteArray {
        val t = Id3.parse(file)!!
        return file.copyOfRange(t.length, file.size)
    }

    @Test
    fun liestV23Texte() {
        val file = tag(3, frame(3, "TIT2", text("Song")), frame(3, "TPE1", utf16("Künstler ♫"))) + audio
        val t = Id3.readText(file)
        assertEquals("Song", t["TIT2"])
        assertEquals("Künstler ♫", t["TPE1"])
    }

    @Test
    fun aendertNurGewuenschteFelderUndBehaeltCover() {
        val file = tag(3, frame(3, "TIT2", text("Alt")), frame(3, "APIC", cover), frame(3, "TALB", text("Album"))) + audio
        val out = Id3.write(file, TagChanges(title = "Neu", genre = "Rock"))
        val t = Id3.readText(out)
        assertEquals("Neu", t["TIT2"])
        assertEquals("Album", t["TALB"])
        assertEquals("Rock", t["TCON"])
        val apic = Id3.parse(out)!!.frames.single { it.id == "APIC" }
        assertArrayEquals(cover, apic.data)
        assertArrayEquals(audio, audioOf(out))
        assertEquals(listOf("TIT2", "APIC", "TALB", "TCON"), Id3.parse(out)!!.frames.map { it.id })
    }

    @Test
    fun leererTextEntferntFeld() {
        val file = tag(3, frame(3, "TIT2", text("X")), frame(3, "TCON", text("Pop"))) + audio
        val out = Id3.write(file, TagChanges(genre = ""))
        assertNull(Id3.readText(out)["TCON"])
        assertEquals("X", Id3.readText(out)["TIT2"])
    }

    @Test
    fun ohneTagWirdV23Angelegt() {
        val out = Id3.write(audio, TagChanges(title = "Grüße", artist = "Ünal", year = "1999", track = "3/12"))
        val t = Id3.parse(out)!!
        assertEquals(3, t.major)
        val txt = Id3.readText(out)
        assertEquals("Grüße", txt["TIT2"])
        assertEquals("Ünal", txt["TPE1"])
        assertEquals("1999", txt["TYER"])
        assertEquals("3/12", txt["TRCK"])
        assertArrayEquals(audio, audioOf(out))
    }

    @Test
    fun unicodeAusserhalbLatin1() {
        val out = Id3.write(audio, TagChanges(title = "東京 – Ελλάδα"))
        assertEquals("東京 – Ελλάδα", Id3.readText(out)["TIT2"])
    }

    @Test
    fun v24BleibtV24MitTdrcUndUtf8() {
        val file = tag(4, frame(4, "TIT2", text("A")), frame(4, "TDRC", text("2001-05-01")), frame(4, "APIC", cover)) + audio
        val out = Id3.write(file, TagChanges(year = "2005", artist = "Сергей"))
        val t = Id3.parse(out)!!
        assertEquals(4, t.major)
        val txt = Id3.readText(out)
        assertEquals("2005", txt["TDRC"])
        assertEquals("Сергей", txt["TPE1"])
        assertFalse(txt.containsKey("TYER"))
        assertArrayEquals(cover, t.frames.single { it.id == "APIC" }.data)
        assertArrayEquals(audio, audioOf(out))
    }

    @Test
    fun grosseFramesInV24Syncsafe() {
        val big = ByteArray(300_000) { (it % 251).toByte() }
        val file = tag(4, frame(4, "APIC", big), frame(4, "TIT2", text("t"))) + audio
        val out = Id3.write(file, TagChanges(title = "neu"))
        assertArrayEquals(big, Id3.parse(out)!!.frames.single { it.id == "APIC" }.data)
        assertArrayEquals(audio, audioOf(out))
    }

    @Test
    fun v22WirdNachV23Konvertiert() {
        fun f22(id: String, d: ByteArray) = id.toByteArray() + byteArrayOf((d.size shr 16).toByte(), (d.size shr 8).toByte(), d.size.toByte()) + d
        val file = tag(2, f22("TT2", text("Alt")), f22("TAL", text("Platte")), f22("PIC", ByteArray(20))) + audio
        val out = Id3.write(file, TagChanges(title = "Neu"))
        val t = Id3.parse(out)!!
        assertEquals(3, t.major)
        val txt = Id3.readText(out)
        assertEquals("Neu", txt["TIT2"])
        assertEquals("Platte", txt["TALB"])
        assertArrayEquals(audio, audioOf(out))
    }

    @Test
    fun unsynchronisierterV23Tag() {
        val body = frame(3, "TIT2", byteArrayOf(0, 'a'.code.toByte(), 0xFF.toByte(), 'b'.code.toByte()))
        // Unsynchronisation: nach 0xFF ein 0x00 einfügen
        val unsynced = ByteArrayOutputStream().apply {
            body.forEach { b -> write(b.toInt()); if (b == 0xFF.toByte()) write(0) }
        }.toByteArray() + ByteArray(10)
        val file = "ID3".toByteArray() + byteArrayOf(3, 0, 0x80.toByte()) + syncsafe(unsynced.size) + unsynced + audio
        val out = Id3.write(file, TagChanges(artist = "K"))
        assertEquals("aÿb", Id3.readText(out)["TIT2"])
        assertEquals("K", Id3.readText(out)["TPE1"])
        assertArrayEquals(audio, audioOf(out))
    }

    @Test
    fun id3v1WirdAktualisiert() {
        val v1 = ByteArray(128).also {
            "TAG".toByteArray().copyInto(it, 0)
            "Old Title".toByteArray().copyInto(it, 3)
            it[127] = 0
        }
        val file = tag(3, frame(3, "TIT2", text("Old Title"))) + audio + v1
        val out = Id3.write(file, TagChanges(title = "New", genre = "Rock", track = "7"))
        val tail = out.copyOfRange(out.size - 128, out.size)
        assertEquals("TAG", String(tail, 0, 3))
        assertEquals("New", String(tail, 3, 30, Charsets.ISO_8859_1).trimEnd('\u0000'))
        assertEquals(17, tail[127].toInt() and 0xFF)
        assertEquals(7, tail[126].toInt())
        assertEquals(0, tail[125].toInt())
    }

    @Test
    fun mehrfachesSchreibenIstStabil() {
        var file = tag(3, frame(3, "APIC", cover)) + audio
        repeat(5) { i -> file = Id3.write(file, TagChanges(title = "T$i")) }
        assertEquals("T4", Id3.readText(file)["TIT2"])
        assertEquals(1, Id3.parse(file)!!.frames.count { it.id == "TIT2" })
        assertArrayEquals(audio, audioOf(file))
    }

    @Test
    fun keineAenderungen() {
        assertTrue(TagChanges().isEmpty)
        assertFalse(TagChanges(genre = "").isEmpty)
    }
}
