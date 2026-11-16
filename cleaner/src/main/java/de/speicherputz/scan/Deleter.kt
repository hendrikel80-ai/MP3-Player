package de.speicherputz.scan

import java.io.File

data class DeleteReport(
    val deleted: List<String>,
    val freedBytes: Long,
    val failed: List<Pair<String, String>>,
)

/**
 * Löscht ausschließlich die vom Nutzer ausgewählten Funde – mit Sicherheitsprüfungen direkt vor dem Löschen:
 * - nur innerhalb des internen Speichers, nie in Android/data oder Android/obb
 * - nie „.nomedia“-Dateien
 * - Dateien, die sich seit dem Suchlauf verändert haben, werden übersprungen (könnten gerade benutzt werden)
 * - Ordner werden nur gelöscht, wenn sie immer noch keine einzige Datei enthalten
 */
class Deleter(root: File) {
    private val rootCanonical = root.canonicalFile

    fun delete(items: Collection<FoundItem>): DeleteReport {
        val deleted = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()
        var freed = 0L
        for (item in items.distinctBy { it.path }) {
            val file = File(item.path)
            val problem = check(file, item)
            if (problem != null) {
                failed += item.relativePath to problem
                continue
            }
            val ok = if (item.isDirectory) deleteEmptyTree(file) else file.delete()
            if (ok && !file.exists()) {
                deleted += item.path
                freed += item.size
            } else {
                failed += item.relativePath to "Konnte nicht gelöscht werden"
            }
        }
        return DeleteReport(deleted, freed, failed)
    }

    private fun check(file: File, item: FoundItem): String? {
        if (!file.exists()) return "Existiert nicht mehr"
        val canonical = try {
            file.canonicalFile
        } catch (_: Exception) {
            return "Pfad nicht prüfbar"
        }
        if (canonical == rootCanonical || !canonical.path.startsWith(rootCanonical.path + File.separator)) {
            return "Liegt außerhalb des erlaubten Bereichs"
        }
        val rel = canonical.path.removePrefix(rootCanonical.path).trimStart(File.separatorChar).replace(File.separatorChar, '/')
        if (Rules.isExcludedDir(rel)) return "Geschützter Systembereich"
        if (Rules.isProtectedFile(file.name)) return "Geschützte Datei"
        if (item.isDirectory) {
            if (!file.isDirectory) return "Ist kein Ordner mehr"
            if (containsAnyFile(file)) return "Ordner ist nicht mehr leer"
        } else {
            if (!file.isFile) return "Ist keine Datei"
            if (file.length() != item.size || file.lastModified() != item.lastModified) {
                return "Wurde seit der Suche verändert – zur Sicherheit übersprungen"
            }
        }
        return null
    }

    private fun containsAnyFile(dir: File): Boolean {
        val children = dir.listFiles() ?: return true
        return children.any { !it.isDirectory || containsAnyFile(it) }
    }

    private fun deleteEmptyTree(dir: File): Boolean {
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory && !deleteEmptyTree(child)) return false
        }
        return dir.delete()
    }
}
