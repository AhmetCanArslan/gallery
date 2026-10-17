package com.arslan.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * File operations on media. Every function keeps MediaStore and the favorites in sync and must be
 * called from a coroutine, the work itself runs on the IO dispatcher.
 */
class FileOps(context: Context) {

    private val context = context.applicationContext
    private val prefs get() = context.prefs

    /** Moves [media] into the trash, returns the trash paths of the moved items. */
    suspend fun moveToTrash(media: Collection<Medium>): List<String> = io {
        val moved = ArrayList<String>()
        val removed = ArrayList<String>()
        media.forEach { medium ->
            Trash.move(context, medium)?.let {
                moved.add(it)
                removed.add(medium.path)
            }
        }
        forgetPaths(removed)
        moved
    }

    /** Restores trashed items, returns their new paths. */
    suspend fun restore(trashPaths: Collection<String>): List<String> = io {
        val restored = Trash.restore(context, trashPaths)
        scan(restored)
        restored
    }

    /** Permanently deletes files, trashed ones included. Returns the deleted paths. */
    suspend fun delete(paths: Collection<String>): List<String> = io {
        val (trashed, regular) = paths.partition { it.contains("/${Trash.DIR_NAME}/") }
        val deleted = Trash.delete(context, trashed).toMutableList()
        val deletedRegular = regular.filter { File(it).delete() || !File(it).exists() }
        forgetPaths(deletedRegular)
        deleted.addAll(deletedRegular)
        deleted
    }

    suspend fun emptyTrash() = io { Trash.empty(context) }

    /** Copies or moves [paths] into [destination], returns the new paths. */
    suspend fun transfer(paths: Collection<String>, destination: String, move: Boolean): List<String> = io {
        val folder = File(destination)
        folder.mkdirs()
        val created = ArrayList<String>()
        val renamed = HashMap<String, String>()
        paths.forEach { path ->
            val source = File(path)
            if (source.parent == folder.absolutePath) return@forEach
            val target = uniqueFile(File(folder, source.name))
            val success = if (move) {
                moveFile(source, target)
            } else {
                try {
                    source.copyTo(target)
                    target.setLastModified(source.lastModified())
                    true
                } catch (e: Exception) {
                    target.delete()
                    false
                }
            }
            if (success) {
                created.add(target.absolutePath)
                if (move) renamed[path] = target.absolutePath
            }
        }

        if (move) {
            forgetPaths(renamed.keys, keepFavorites = true)
            moveFavorites(renamed)
        }
        scan(created)
        created
    }

    /** Renames a file or folder, returns the new path or null when it failed. */
    suspend fun rename(path: String, newName: String): String? = io {
        val source = File(path)
        val target = File(source.parentFile, newName.trim())
        if (newName.isBlank() || newName.contains('/') || target.exists() || !source.renameTo(target)) return@io null

        if (target.isDirectory) {
            val children = target.listFiles()?.filter { it.name.isMediaFile() }.orEmpty()
            val oldPaths = children.map { "$path/${it.name}" }
            forgetPaths(oldPaths, keepFavorites = true)
            moveFavorites(oldPaths.zip(children.map { it.absolutePath }).toMap())
            replaceFolderInPrefs(path, target.absolutePath)
            scan(children.map { it.absolutePath })
        } else {
            forgetPaths(listOf(path), keepFavorites = true)
            moveFavorites(mapOf(path to target.absolutePath))
            scan(listOf(target.absolutePath))
        }
        target.absolutePath
    }

    /** Hides a file by prefixing its name with a dot, or unhides it. Returns the new path. */
    suspend fun setFileHidden(path: String, hidden: Boolean): String? {
        val name = path.filename
        val newName = if (hidden) ".$name" else name.trimStart('.')
        if (newName == name || newName.isEmpty()) return path
        return rename(path, newName)
    }

    /** Hides a folder from every gallery with a ".nomedia" file, or unhides it. */
    suspend fun setFolderHidden(folder: String, hidden: Boolean): Boolean = io {
        val nomedia = File(folder, MediaLibrary.NOMEDIA)
        val media = File(folder).listFiles()?.filter { it.isFile && it.name.isMediaFile() }?.map { it.absolutePath }.orEmpty()
        if (hidden) {
            val created = nomedia.exists() || nomedia.createNewFile()
            // Rescanning makes MediaStore notice the ".nomedia" file and drop the folder's media.
            if (created) scan(media)
            created
        } else {
            val removed = !nomedia.exists() || nomedia.delete()
            if (removed) scan(media)
            removed
        }
    }

    /**
     * Rotates an image by [degrees]. Formats with EXIF support only get their orientation tag changed,
     * so the rotation is lossless. Returns false when the format can't be rotated.
     */
    suspend fun rotate(path: String, degrees: Int): Boolean = io {
        try {
            val exifFormats = setOf("jpg", "jpeg", "png", "webp")
            if (path.extension in exifFormats) {
                ExifInterface(path).apply {
                    rotate(degrees)
                    saveAttributes()
                }
            } else {
                val bitmap = BitmapFactory.decodeFile(path) ?: return@io false
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
                val format = when (path.extension) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> Bitmap.CompressFormat.WEBP
                    "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                    else -> return@io false
                }
                File(path).outputStream().use { rotated.compress(format, 95, it) }
            }
            scan(listOf(path))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Makes MediaStore pick up new or changed files and waits until it did. */
    suspend fun scan(paths: Collection<String>) {
        if (paths.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            val remaining = AtomicInteger(paths.size)
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                if (remaining.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
            }
        }
    }

    /** Drops files that no longer exist at [paths] from MediaStore right away, instead of waiting for a scan. */
    private suspend fun forgetPaths(allPaths: Collection<String>, keepFavorites: Boolean = false) {
        // Never pass existing files, deleting their MediaStore entry deletes the file too.
        val paths = allPaths.filterNot { File(it).exists() }
        if (paths.isEmpty()) return
        try {
            paths.chunked(500).forEach { chunk ->
                val selection = "${MediaStore.MediaColumns.DATA} IN (${chunk.joinToString(",") { "?" }})"
                context.contentResolver.delete(MediaStore.Files.getContentUri("external"), selection, chunk.toTypedArray())
            }
        } catch (e: Exception) {
            scan(paths)
        }

        if (!keepFavorites) {
            val favorites = prefs.favorites.toMutableSet()
            if (favorites.removeAll(paths.toSet())) prefs.favorites = favorites
        }
    }

    private fun moveFavorites(renamed: Map<String, String>) {
        val favorites = prefs.favorites.toMutableSet()
        var changed = false
        renamed.forEach { (old, new) ->
            if (favorites.remove(old)) {
                favorites.add(new)
                changed = true
            }
        }
        if (changed) prefs.favorites = favorites
    }

    private fun replaceFolderInPrefs(old: String, new: String) {
        prefs.pinnedFolders.let { if (old in it) prefs.pinnedFolders = it - old + new }
        prefs.excludedFolders.let { if (old in it) prefs.excludedFolders = it - old + new }
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
