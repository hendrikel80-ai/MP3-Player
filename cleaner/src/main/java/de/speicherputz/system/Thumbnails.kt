package de.speicherputz.system

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.io.File

/** Kleine Vorschaubilder für Fotos und Videos in der Dateiliste. */
object Thumbnails {
    private val cache = LruCache<String, Bitmap>(150)
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
    private val videoExt = setOf("mp4", "3gp", "mkv", "webm", "mov", "avi", "m4v")

    fun isImage(name: String) = name.substringAfterLast('.', "").lowercase() in imageExt
    fun isVideo(name: String) = name.substringAfterLast('.', "").lowercase() in videoExt

    /** Blockiert – nur im Hintergrund aufrufen. */
    fun load(path: String, sizePx: Int = 160): Bitmap? {
        cache.get(path)?.let { return it }
        val name = path.substringAfterLast('/').removePrefix(".trashed-")
        val bitmap = try {
            when {
                isImage(name) -> loadImage(path, sizePx)
                isVideo(name) -> loadVideo(path, sizePx)
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
        if (bitmap != null) cache.put(path, bitmap)
        return bitmap
    }

    private fun loadImage(path: String, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ThumbnailUtils.createImageThumbnail(File(path), Size(sizePx, sizePx), null)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    @Suppress("DEPRECATION")
    private fun loadVideo(path: String, sizePx: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThumbnailUtils.createVideoThumbnail(File(path), Size(sizePx, sizePx), null)
        } else {
            ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
        }
}
