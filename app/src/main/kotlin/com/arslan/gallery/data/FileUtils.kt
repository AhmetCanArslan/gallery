package com.arslan.gallery.data

import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

private val photoExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "bmp", "jxl")
private val rawExtensions = setOf("dng", "cr2", "cr3", "nef", "nrw", "arw", "orf", "rw2", "raf", "pef", "srw")
private val videoExtensions = setOf("mp4", "mkv", "webm", "3gp", "mov", "m4v", "avi", "ts")

val String.filename get() = substringAfterLast('/')

val String.parentPath get() = substringBeforeLast('/', "")

val String.extension get() = substringAfterLast('.', "").lowercase(Locale.ROOT)

/** Media type of a file name, 0 when it isn't a supported photo or video. */
fun mediaTypeOf(name: String, mimeType: String? = null): Int {
    val ext = name.extension
    return when {
        ext == "gif" || mimeType == "image/gif" -> TYPE_GIF
        ext in rawExtensions -> TYPE_RAW
        ext in videoExtensions || mimeType?.startsWith("video/") == true -> TYPE_VIDEO
        ext in photoExtensions || mimeType?.startsWith("image/") == true -> TYPE_IMAGE
        else -> 0
    }
}

fun String.isMediaFile() = mediaTypeOf(this) != 0

fun String.isVideoFile() = mediaTypeOf(this) == TYPE_VIDEO

fun String.mimeType(): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: if (isVideoFile()) "video/*" else "image/*"

/** Whether this build may modify arbitrary files on shared storage. */
fun hasAllFilesAccess() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

/** [file] renamed to "name (1).ext", "name (2).ext", ... until no such file exists. */
fun uniqueFile(file: File): File {
    if (!file.exists()) return file
    val base = file.nameWithoutExtension
    val ext = file.extension.let { if (it.isEmpty()) "" else ".$it" }
    var index = 1
    var candidate: File
    do {
        candidate = File(file.parentFile, "$base ($index)$ext")
        index++
    } while (candidate.exists())
    return candidate
}

fun Long.formatSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$this B" else String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

/** Seconds as "m:ss" or "h:mm:ss". */
fun Int.formatDuration(): String {
    val hours = this / 3600
    val minutes = this % 3600 / 60
    val seconds = this % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
