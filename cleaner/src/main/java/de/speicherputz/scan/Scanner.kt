package de.speicherputz.scan

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Durchsucht den internen Speicher (ohne Android/data und Android/obb) und sammelt Vorschläge.
 * Es wird hier NICHTS gelöscht.
 *
 * @param apkNote liefert für eine APK-Datei einen Hinweis (z. B. „App ist installiert“).
 */
class Scanner(
    private val root: File,
    private val now: Long = System.currentTimeMillis(),
    private val apkNote: (File) -> String? = { null },
    private val onProgress: (filesScanned: Int, currentDir: String) -> Unit = { _, _ -> },
) {
    private val rootPath = root.absolutePath.trimEnd('/')
    private val found = Category.entries.associateWith { mutableListOf<FoundItem>() }
    private val duplicateCandidates = mutableListOf<FoundItem>()
    private var filesScanned = 0

    suspend fun scan(): ScanResult {
        val start = System.currentTimeMillis()
        visitDir(root, depth = 0)
        onProgress(filesScanned, "Suche doppelte Dateien …")
        found.getValue(Category.DUPLICATES).addAll(findDuplicates(duplicateCandidates))
        val items = found
            .mapValues { (category, list) ->
                if (category == Category.DUPLICATES) list.toList() else list.sortedByDescending { it.size }
            }
            .filterValues { it.isNotEmpty() }
        return ScanResult(rootPath, items, filesScanned, System.currentTimeMillis() - start)
    }

    private fun relative(file: File): String = file.absolutePath.removePrefix(rootPath).trimStart('/')

    /**
     * Besucht einen Ordner rekursiv.
     * @return Liste der leeren (vorschlagbaren) Ordner darin – oder `null`, wenn der Ordner NICHT leer ist.
     */
    private suspend fun visitDir(dir: File, depth: Int): List<FoundItem>? {
        currentCoroutineContext().ensureActive()
        val rel = relative(dir)
        if (depth > 0 && Rules.isExcludedDir(rel)) return null
        if (depth > 40) return null
        // Nicht lesbar → als „nicht leer“ behandeln, damit er nie als leer vorgeschlagen wird.
        val children = dir.listFiles() ?: return null
        if (filesScanned % 200 == 0) onProgress(filesScanned, rel)

        var isEmpty = true
        val emptyInside = mutableListOf<FoundItem>()
        for (child in children) {
            if (isSymlink(child)) {
                isEmpty = false
                continue
            }
            if (child.isDirectory) {
                val sub = visitDir(child, depth + 1)
                if (sub == null) isEmpty = false else emptyInside += sub
            } else {
                isEmpty = false
                visitFile(child)
            }
        }
        if (!isEmpty) {
            found.getValue(Category.EMPTY_DIRS).addAll(emptyInside)
            return null
        }
        // Ordner ist leer: entweder er selbst wird vorgeschlagen, oder (falls geschützt) seine leeren Unterordner.
        return if (depth > 0 && Rules.mayProposeEmptyDir(rel)) {
            listOf(FoundItem(dir.absolutePath, rel, 0, dir.lastModified(), isDirectory = true, note = "Leerer Ordner"))
        } else {
            found.getValue(Category.EMPTY_DIRS).addAll(emptyInside)
            emptyList()
        }
    }

    private fun visitFile(file: File) {
        filesScanned++
        val rel = relative(file)
        val size = file.length()
        val modified = file.lastModified()
        val cls = Rules.classify(rel, size, modified, now)
        if (cls != null) {
            val note = if (cls.category == Category.APK) apkNote(file) ?: cls.note else cls.note
            found.getValue(cls.category) += FoundItem(file.absolutePath, rel, size, modified, note = note)
        }
        if (Rules.isProtectedFile(file.name) || !Rules.mayBeLargeOrDuplicate(cls?.category)) return
        if (size >= Rules.LARGE_FILE_BYTES) {
            found.getValue(Category.LARGE) += FoundItem(file.absolutePath, rel, size, modified, note = cls?.note)
        }
        if (size >= Rules.DUPLICATE_MIN_BYTES) {
            duplicateCandidates += FoundItem(file.absolutePath, rel, size, modified)
        }
    }

    private fun isSymlink(file: File): Boolean =
        try {
            Files.isSymbolicLink(file.toPath())
        } catch (_: Exception) {
            true
        }

    private suspend fun findDuplicates(candidates: List<FoundItem>): List<FoundItem> {
        val result = mutableListOf<FoundItem>()
        var groupId = 0
        val bySize = candidates.groupBy { it.size }.filterValues { it.size > 1 }
        // Größte Gruppen zuerst (bringen am meisten Platz)
        for ((_, sameSize) in bySize.entries.sortedByDescending { it.key }) {
            currentCoroutineContext().ensureActive()
            val byHead = sameSize.groupBy { hash(File(it.path), HEAD_BYTES) }.filter { it.key != null && it.value.size > 1 }
            for ((_, sameHead) in byHead) {
                val byFull = sameHead.groupBy { hash(File(it.path), Long.MAX_VALUE) }.filter { it.key != null && it.value.size > 1 }
                for ((_, group) in byFull) {
                    currentCoroutineContext().ensureActive()
                    val sorted = group.sortedWith(
                        compareBy<FoundItem>({ Rules.originalScore(it.relativePath) }, { it.lastModified }, { it.relativePath.length }),
                    )
                    val original = sorted.first()
                    groupId++
                    sorted.forEachIndexed { index, item ->
                        result += item.copy(
                            groupId = groupId,
                            isOriginal = index == 0,
                            note = if (index == 0) "Original – behalten empfohlen" else "Kopie von „${original.relativePath}“",
                        )
                    }
                }
            }
        }
        return result
    }

    private fun hash(file: File, maxBytes: Long): String? =
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                var remaining = maxBytes
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    remaining -= read
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val HEAD_BYTES = 64L * 1024
    }
}
