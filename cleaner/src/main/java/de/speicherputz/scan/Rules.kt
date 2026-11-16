package de.speicherputz.scan

import java.util.Locale

/**
 * Regeln, welche Dateien als überflüssig gelten. Alle Pfade sind relativ zum internen Speicher
 * (z. B. „DCIM/.thumbnails/123.jpg“), Trennzeichen „/“.
 *
 * Grundsatz: lieber etwas übersehen als etwas Wichtiges vorschlagen.
 */
object Rules {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val LARGE_FILE_BYTES = 100L * 1024 * 1024
    const val DUPLICATE_MIN_BYTES = 512L * 1024
    const val TEMP_MIN_AGE_MS = DAY_MS
    const val OLD_DOWNLOAD_AGE_MS = 90 * DAY_MS

    /** Ordner, die nie durchsucht werden (App-interne Daten, Systembereiche). */
    private val EXCLUDED_DIRS = listOf("android/data", "android/obb", "android/.trash", ".android_secure", "lost+found")

    /** Standardordner, die nie als „leer“ gelöscht werden. */
    private val STANDARD_DIRS = setOf(
        "dcim", "camera", "download", "downloads", "music", "pictures", "movies", "documents", "android",
        "alarms", "notifications", "ringtones", "podcasts", "audiobooks", "recordings", "screenshots",
        "screenrecorder", "screen recordings", "dcim/camera", "dcim/screenshots", "pictures/screenshots",
    )

    private val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "dmp", "crdownload", "part", "partial")
    private val TEMP_NAMES = setOf("thumbs.db", ".ds_store", "desktop.ini")
    private val APK_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm")
    private val TELEGRAM_MEDIA_DIRS = setOf("telegram images", "telegram video", "telegram documents", "telegram audio")
    private val WHATSAPP_OLD_BACKUP = Regex("""^msgstore-\d{4}-\d{2}-\d{2}\.\d+\.db\.crypt\d+$""")

    private fun String.lower() = lowercase(Locale.ROOT)

    fun extension(name: String): String = name.substringAfterLast('.', "").lower()

    /** Dateien, die nie gelöscht werden dürfen – egal in welchem Ordner. */
    fun isProtectedFile(name: String): Boolean {
        val n = name.lower()
        // .nomedia verhindert, dass versteckte Bilder (z. B. Vorschaubilder) in der Galerie auftauchen.
        return n == ".nomedia"
    }

    fun isExcludedDir(relativeDir: String): Boolean {
        val r = relativeDir.lower().trim('/')
        return EXCLUDED_DIRS.any { r == it || r.startsWith("$it/") }
    }

    /** Darf dieser (leere) Ordner vorgeschlagen werden? */
    fun mayProposeEmptyDir(relativeDir: String): Boolean {
        val r = relativeDir.lower().trim('/')
        if (r.isEmpty() || !r.contains('/')) return false // Ordner direkt im Hauptverzeichnis: nie
        if (r == "android" || r.startsWith("android/")) return false
        if (r in STANDARD_DIRS || r.substringAfterLast('/') in STANDARD_DIRS) return false
        return true
    }

    data class Classification(val category: Category, val note: String? = null)

    /**
     * Ordnet eine Datei einer Kategorie zu – oder `null`, wenn sie nicht vorgeschlagen wird.
     * „Große Dateien“ und „Duplikate“ werden separat ermittelt.
     */
    fun classify(relativePath: String, size: Long, lastModified: Long, now: Long): Classification? {
        val rel = relativePath.lower()
        val name = rel.substringAfterLast('/')
        if (isProtectedFile(name)) return null
        val dirs = rel.split('/').dropLast(1)
        val ext = extension(name)
        val age = now - lastModified

        if (name.startsWith(".trashed-")) {
            return Classification(Category.TRASH, "Im Papierkorb – bereits von dir gelöscht")
        }
        if (".thumbnails" in dirs) {
            return Classification(Category.THUMBNAILS, "Vorschaubild, wird bei Bedarf neu erstellt")
        }
        if (age >= TEMP_MIN_AGE_MS) {
            val tempNote = when {
                name in TEMP_NAMES -> "Systemrest von Windows/Mac"
                name.startsWith("._") && size <= 4096 -> "Systemrest vom Mac"
                ext == "crdownload" || ext == "part" || ext == "partial" -> "Abgebrochener Download"
                ext == "log" -> "Protokolldatei"
                ext == "dmp" -> "Absturzbericht"
                ext == "tmp" || ext == "temp" -> "Temporäre Datei"
                else -> null
            }
            if (tempNote != null) return Classification(Category.TEMP, tempNote)
        }
        if (ext in APK_EXTENSIONS) {
            return Classification(Category.APK)
        }
        messengerNote(dirs, name)?.let { return Classification(Category.MESSENGER, it) }
        if ((dirs.firstOrNull() == "download" || dirs.firstOrNull() == "downloads") && age >= OLD_DOWNLOAD_AGE_MS) {
            return Classification(Category.OLD_DOWNLOADS, "Seit ${age / DAY_MS} Tagen unverändert")
        }
        return null
    }

    private fun messengerNote(dirs: List<String>, name: String): String? {
        val isWhatsApp = dirs.any { it == "whatsapp" || it == "whatsapp business" }
        if (isWhatsApp) {
            if ("media" in dirs && "sent" in dirs) return "WhatsApp: Kopie einer Datei, die du verschickt hast"
            if (".statuses" in dirs) return "WhatsApp: angesehener Status"
            if ("databases" in dirs && WHATSAPP_OLD_BACKUP.matches(name)) {
                return "WhatsApp: ältere Chat-Sicherung (die aktuelle Sicherung bleibt erhalten)"
            }
            return null
        }
        if (dirs.any { it == "telegram" } && dirs.any { it in TELEGRAM_MEDIA_DIRS }) {
            return "Telegram: heruntergeladen, bleibt in Telegram verfügbar"
        }
        return null
    }

    /** Darf die Datei zusätzlich als „große Datei“ / Duplikat vorgeschlagen werden? */
    fun mayBeLargeOrDuplicate(primary: Category?): Boolean =
        primary == null || primary == Category.OLD_DOWNLOADS || primary == Category.MESSENGER

    /**
     * Wählt in einer Gruppe gleicher Dateien das „Original“ aus, das behalten werden soll:
     * bevorzugt die Kamera-Aufnahme bzw. nicht Download/WhatsApp, dann die älteste Datei.
     */
    fun originalScore(relativePath: String): Int {
        val r = relativePath.lower()
        return when {
            r.startsWith("dcim/") -> 0
            r.startsWith("pictures/") || r.startsWith("music/") || r.startsWith("movies/") || r.startsWith("documents/") -> 1
            r.contains("whatsapp") || r.contains("telegram") || r.startsWith("download") -> 3
            else -> 2
        }
    }

    /**
     * Gibt die Duplikat-Gruppen zurück, in denen ALLE Kopien ausgewählt sind
     * (dann würde die Datei komplett verloren gehen).
     */
    fun fullySelectedGroups(duplicates: List<FoundItem>, selected: Set<String>): Set<Int> =
        duplicates.groupBy { it.groupId }
            .filter { (_, members) -> members.size > 1 && members.all { it.path in selected } }
            .keys
}
