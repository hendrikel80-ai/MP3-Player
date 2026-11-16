package de.speicherputz.system

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import java.io.File

/** Alles, was mit Android-Systemfunktionen zu tun hat. */
object Device {

    val storageRoot: File get() = Environment.getExternalStorageDirectory()

    // ---------- Speicher-Berechtigung ----------

    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Android 11+: Einstellungsseite „Zugriff auf alle Dateien“ für diese App. */
    fun allFilesAccessIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        } else {
            null
        }

    fun allFilesAccessFallbackIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) else null

    val legacyStoragePermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    fun startSafely(context: Context, vararg intents: Intent?): Boolean {
        for (intent in intents.filterNotNull()) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    // ---------- Speicher-Übersicht ----------

    data class StorageOverview(val totalBytes: Long, val freeBytes: Long) {
        val usedBytes get() = totalBytes - freeBytes
        val usedFraction get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    }

    fun storageOverview(context: Context): StorageOverview {
        return try {
            val ssm = context.getSystemService(StorageStatsManager::class.java)
            StorageOverview(ssm.getTotalBytes(StorageManager.UUID_DEFAULT), ssm.getFreeBytes(StorageManager.UUID_DEFAULT))
        } catch (_: Exception) {
            val dir = Environment.getDataDirectory()
            StorageOverview(dir.totalSpace, dir.usableSpace)
        }
    }

    // ---------- APK-Dateien ----------

    /** Hinweistext zu einer APK-Datei: Ist die App schon installiert? */
    fun apkNote(context: Context, file: File): String? {
        return try {
            val pm = context.packageManager
            val archive = pm.getPackageArchiveInfo(file.path, 0) ?: return "Installationsdatei (Inhalt nicht lesbar)"
            val appInfo = archive.applicationInfo
            val label = appInfo?.let {
                it.sourceDir = file.path
                it.publicSourceDir = file.path
                it.loadLabel(pm).toString()
            } ?: archive.packageName
            val installed = try {
                pm.getPackageInfo(archive.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            when {
                installed == null -> "„$label“ – App ist nicht installiert. Nur löschen, wenn du sie nicht mehr installieren willst."
                versionCode(installed) >= versionCode(archive) -> "„$label“ – ist bereits installiert, Datei wird nicht mehr gebraucht"
                else -> "„$label“ – neuere Version als installiert (Update noch nicht installiert?)"
            }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    // ---------- App-Caches ----------

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntents(context: Context): Array<Intent> = arrayOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    )

    data class AppCache(
        val packageName: String,
        val label: String,
        val cacheBytes: Long,
        val totalBytes: Long,
        val icon: Bitmap?,
        val isSystemApp: Boolean,
    )

    /** Liest die Cache-Größe aller Apps. Benötigt „Nutzungsdatenzugriff“. */
    fun appCaches(context: Context, minBytes: Long = 1024L * 1024): List<AppCache> {
        val pm = context.packageManager
        val ssm = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        return pm.getInstalledApplications(0).mapNotNull { app ->
            try {
                val stats = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, user)
                if (stats.cacheBytes < minBytes) return@mapNotNull null
                AppCache(
                    packageName = app.packageName,
                    label = app.loadLabel(pm).toString(),
                    cacheBytes = stats.cacheBytes,
                    totalBytes = stats.appBytes + stats.dataBytes,
                    icon = try {
                        app.loadIcon(pm).toBitmap(96, 96)
                    } catch (_: Exception) {
                        null
                    },
                    isSystemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.cacheBytes }
    }

    fun appDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    /** Eigenen Cache leeren (der Cache dieser App selbst). */
    fun clearOwnCache(context: Context) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    // ---------- Dateien ----------

    fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

    /** Öffnet eine Datei zum Ansehen in einer passenden App. */
    fun openFile(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType(file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Öffnen mit").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Teilt der Galerie & Co. mit, dass Dateien gelöscht wurden. */
    fun notifyMediaStore(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(context.applicationContext, paths.toTypedArray(), null, null)
    }
}
