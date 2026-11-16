package de.speicherputz.scan

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ScannerTest {
    private lateinit var root: File
    private val now = 1_800_000_000_000L
    private val old = now - 200 * Rules.DAY_MS
    private val recent = now - 60_000

    @Before
    fun setUp() {
        root = Files.createTempDirectory("speicher").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun file(rel: String, bytes: ByteArray = ByteArray(10) { 1 }, modified: Long = old): File {
        val f = File(root, rel)
        f.parentFile!!.mkdirs()
        f.writeBytes(bytes)
        f.setLastModified(modified)
        return f
    }

    private fun scan(): ScanResult = runBlocking { Scanner(root, now).scan() }

    private fun ScanResult.rel(c: Category) = of(c).map { it.relativePath }.toSet()

    @Test
    fun findsSafeJunk() {
        file("DCIM/.thumbnails/1.jpg")
        file("DCIM/.thumbnails/.nomedia")
        file("Pictures/.trashed-1700000000-IMG_1.jpg")
        file("Download/bild.jpg.crdownload")
        file("Documents/app.log")
        file("Documents/fresh.log", modified = recent)
        file("Download/Thumbs.db")
        file("Download/app-release.apk", modified = recent)

        val r = scan()
        assertEquals(setOf("DCIM/.thumbnails/1.jpg"), r.rel(Category.THUMBNAILS))
        assertEquals(setOf("Pictures/.trashed-1700000000-IMG_1.jpg"), r.rel(Category.TRASH))
        assertEquals(setOf("Download/bild.jpg.crdownload", "Documents/app.log", "Download/Thumbs.db"), r.rel(Category.TEMP))
        assertEquals(setOf("Download/app-release.apk"), r.rel(Category.APK))
        // .nomedia und frische Log-Dateien werden nie vorgeschlagen
        assertFalse(r.uniqueItems().values.any { it.name == ".nomedia" || it.name == "fresh.log" })
    }

    @Test
    fun personalFilesAreNotProposed() {
        file("DCIM/Camera/IMG_1.jpg")
        file("Documents/Steuer.pdf")
        file("Music/song.mp3")
        file("Download/neu.pdf", modified = recent)
        val r = scan()
        assertTrue(r.items.isEmpty())
    }

    @Test
    fun skipsAndroidDataAndObb() {
        file("Android/data/com.app/cache/x.tmp")
        file("Android/obb/com.game/main.obb", ByteArray(10))
        file("Android/data/com.app/files/.thumbnails/1.jpg")
        assertTrue(scan().items.isEmpty())
    }

    @Test
    fun whatsAppRules() {
        val wa = "Android/media/com.whatsapp/WhatsApp"
        file("$wa/Media/WhatsApp Images/Sent/IMG-1.jpg")
        file("$wa/Media/WhatsApp Images/IMG-2.jpg")
        file("$wa/Media/.Statuses/abc.mp4")
        file("$wa/Databases/msgstore-2026-11-01.1.db.crypt14")
        file("$wa/Databases/msgstore.db.crypt14")
        val r = scan()
        assertEquals(
            setOf(
                "$wa/Media/WhatsApp Images/Sent/IMG-1.jpg",
                "$wa/Media/.Statuses/abc.mp4",
                "$wa/Databases/msgstore-2026-11-01.1.db.crypt14",
            ),
            r.rel(Category.MESSENGER),
        )
    }

    @Test
    fun oldDownloads() {
        file("Download/alt.pdf")
        file("Download/neu.pdf", modified = recent)
        assertEquals(setOf("Download/alt.pdf"), scan().rel(Category.OLD_DOWNLOADS))
    }

    @Test
    fun emptyFoldersTopmostOnlyAndNeverStandard() {
        File(root, "Movies").mkdirs()             // Standardordner – nie
        File(root, "Telegram").mkdirs()           // direkt im Hauptverzeichnis – nie
        File(root, "Documents/a/b/c").mkdirs()    // nur „Documents/a“ wird vorgeschlagen
        File(root, "Android/media/com.x/y").mkdirs() // Android-Ordner – nie
        file("Pictures/x/.nomedia")               // enthält .nomedia → nicht leer
        File(root, "Pictures/leer").mkdirs()
        assertEquals(setOf("Documents/a", "Pictures/leer"), scan().rel(Category.EMPTY_DIRS))
    }

    @Test
    fun duplicatesKeepCameraOriginal() {
        val content = ByteArray(600 * 1024) { (it % 251).toByte() }
        file("Download/IMG_1.jpg", content, modified = old)
        file("DCIM/Camera/IMG_1.jpg", content, modified = old + 1000)
        file("Pictures/Kopie/IMG_1.jpg", content, modified = old - 1000)
        val different = content.copyOf().also { it[it.size - 1] = 99 }
        file("Pictures/anders.jpg", different)

        val dups = scan().of(Category.DUPLICATES)
        assertEquals(3, dups.size)
        val original = dups.single { it.isOriginal }
        assertEquals("DCIM/Camera/IMG_1.jpg", original.relativePath)
        assertTrue(dups.all { it.groupId == original.groupId })

        val all = dups.map { it.path }.toSet()
        assertEquals(setOf(original.groupId), Rules.fullySelectedGroups(dups, all))
        assertTrue(Rules.fullySelectedGroups(dups, all - original.path).isEmpty())
    }

    @Test
    fun largeFiles() {
        val f = File(root, "Movies/film.mp4")
        f.parentFile!!.mkdirs()
        java.io.RandomAccessFile(f, "rw").use { it.setLength(Rules.LARGE_FILE_BYTES) }
        assertEquals(setOf("Movies/film.mp4"), scan().rel(Category.LARGE))
    }

    @Test
    fun deleterDeletesOnlySafeSelections() {
        val thumb = file("DCIM/.thumbnails/1.jpg")
        val nomedia = file("DCIM/.thumbnails/.nomedia")
        val changed = file("Documents/x.log")
        val r = scan()
        // Datei nach der Suche verändert → wird übersprungen
        changed.writeBytes(ByteArray(99))
        File(root, "Documents/a/b").mkdirs()
        val rEmpty = scan()
        // Leerer Ordner bekommt nachträglich eine Datei → wird nicht gelöscht
        file("Documents/a/b/neu.txt")

        val toDelete = r.of(Category.THUMBNAILS) + r.of(Category.TEMP) + rEmpty.of(Category.EMPTY_DIRS) +
            FoundItem(nomedia.absolutePath, "DCIM/.thumbnails/.nomedia", nomedia.length(), nomedia.lastModified()) +
            FoundItem(root.parentFile!!.absolutePath + "/fremd.txt", "../fremd.txt", 1, 1)
        val report = Deleter(root).delete(toDelete)

        assertFalse(thumb.exists())
        assertTrue(nomedia.exists())
        assertTrue(changed.exists())
        assertTrue(File(root, "Documents/a/b/neu.txt").exists())
        assertEquals(listOf(thumb.absolutePath), report.deleted)
        assertEquals(4, report.failed.size)
    }

    @Test
    fun deleterRemovesEmptyTree() {
        File(root, "Documents/a/b/c").mkdirs()
        val r = scan()
        val report = Deleter(root).delete(r.of(Category.EMPTY_DIRS))
        assertEquals(1, report.deleted.size)
        assertFalse(File(root, "Documents/a").exists())
        assertTrue(File(root, "Documents").exists())
    }

    @Test
    fun classifyIgnoresNomediaEverywhere() {
        assertNull(Rules.classify("DCIM/.thumbnails/.nomedia", 0, 0, now))
    }
}
