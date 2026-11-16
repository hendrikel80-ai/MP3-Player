package de.speicherputz.scan

/** Wie unbedenklich das Löschen einer Kategorie ist. */
enum class Safety(val label: String, val explanation: String) {
    SAFE(
        "Unbedenklich",
        "Das Handy und deine Apps funktionieren ohne diese Dateien weiter – sie werden bei Bedarf neu erstellt " +
            "oder werden nicht mehr gebraucht.",
    ),
    REVIEW(
        "Bitte prüfen",
        "Das Handy funktioniert ohne diese Dateien weiter, aber es können persönliche Dateien dabei sein. " +
            "Schau sie dir an und wähle nur aus, was du wirklich nicht mehr brauchst.",
    ),
}

enum class Category(val title: String, val description: String, val safety: Safety) {
    TRASH(
        "Papierkorb",
        "Fotos und Videos, die du bereits gelöscht hast. Android hebt sie noch bis zu 30 Tage im Papierkorb auf – " +
            "sie belegen so lange weiter Speicher.",
        Safety.SAFE,
    ),
    THUMBNAILS(
        "Vorschaubilder-Cache",
        "Unsichtbare Ordner „.thumbnails“ mit kleinen Vorschaubildern der Galerie. Werden bei Bedarf automatisch neu erstellt.",
        Safety.SAFE,
    ),
    TEMP(
        "Temporäre Dateien & Protokolle",
        "Abgebrochene Downloads, Zwischenspeicher-Dateien (.tmp), Protokolle (.log), Absturzberichte und " +
            "Systemreste von Windows/Mac (Thumbs.db, .DS_Store). Nur Dateien, die älter als 1 Tag sind.",
        Safety.SAFE,
    ),
    EMPTY_DIRS(
        "Leere Ordner",
        "Ordner ohne eine einzige Datei. Apps legen sie bei Bedarf selbst wieder an. Standardordner " +
            "(DCIM, Download, Music …) und der Android-Ordner werden nie angetastet.",
        Safety.SAFE,
    ),
    APK(
        "Installationsdateien (APK)",
        "Heruntergeladene App-Installationsdateien. Ist die App installiert, wird die Datei nicht mehr gebraucht.",
        Safety.SAFE,
    ),
    MESSENGER(
        "Messenger-Kopien & alte Sicherungen",
        "WhatsApp: Kopien von Bildern/Videos, die du selbst verschickt hast, angesehene Status-Meldungen und " +
            "ältere tägliche Chat-Sicherungen (die aktuelle Sicherung bleibt immer erhalten). " +
            "Telegram: heruntergeladene Medien (bleiben in Telegram in der Cloud).",
        Safety.REVIEW,
    ),
    OLD_DOWNLOADS(
        "Alte Downloads",
        "Dateien im Download-Ordner, die seit über 90 Tagen nicht verändert wurden.",
        Safety.REVIEW,
    ),
    LARGE(
        "Große Dateien",
        "Einzelne Dateien ab 100 MB, z. B. Videos. Oft lohnt es sich, sie auf PC oder Cloud zu sichern und dann zu löschen.",
        Safety.REVIEW,
    ),
    DUPLICATES(
        "Doppelte Dateien",
        "Dateien mit exakt gleichem Inhalt an mehreren Stellen. Eine Kopie (das „Original“) bleibt immer erhalten – " +
            "alle Kopien einer Gruppe lassen sich nicht gleichzeitig löschen.",
        Safety.REVIEW,
    ),
}

/** Eine gefundene Datei (oder ein leerer Ordner). */
data class FoundItem(
    val path: String,
    val relativePath: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean = false,
    val note: String? = null,
    /** Nur bei Duplikaten: Nummer der Gruppe gleicher Dateien. */
    val groupId: Int = -1,
    /** Nur bei Duplikaten: die Kopie, die behalten werden sollte. */
    val isOriginal: Boolean = false,
) {
    val name: String get() = relativePath.substringAfterLast('/')
    val folder: String get() = relativePath.substringBeforeLast('/', "")
}

data class ScanResult(
    val rootPath: String,
    val items: Map<Category, List<FoundItem>>,
    val filesScanned: Int,
    val durationMs: Long,
) {
    fun of(category: Category): List<FoundItem> = items[category].orEmpty()

    /** Alle Funde ohne Doppelzählung (eine Datei kann in mehreren Kategorien auftauchen). */
    fun uniqueItems(): Map<String, FoundItem> {
        val map = LinkedHashMap<String, FoundItem>()
        items.values.forEach { list -> list.forEach { map.putIfAbsent(it.path, it) } }
        return map
    }
}
