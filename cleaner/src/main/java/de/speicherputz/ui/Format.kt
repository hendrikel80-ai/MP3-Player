package de.speicherputz.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val german = Locale.GERMANY

/** „1,4 GB“, „350 MB“, „12 KB“ */
fun formatBytes(bytes: Long): String {
    val units = listOf("Bytes", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return when {
        unit == 0 -> "$bytes Bytes"
        value < 10 -> String.format(german, "%.1f %s", value, units[unit])
        else -> String.format(german, "%.0f %s", value, units[unit])
    }
}

fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, german).format(Date(millis))

fun plural(count: Int, one: String, many: String) = if (count == 1) "1 $one" else "$count $many"
