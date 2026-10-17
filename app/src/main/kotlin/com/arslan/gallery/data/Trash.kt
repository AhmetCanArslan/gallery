package com.arslan.gallery.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import org.json.JSONObject
import java.io.File

/**
 * Trash kept in a hidden folder at the root of the volume a file lives on, so trashing is a cheap
 * rename instead of a copy. An index in the app's private storage remembers where items came from.
 */
object Trash {

    const val DIR_NAME = ".gallery-trash"
    private const val INDEX_FILE = "trash.json"
    private const val KEY_ORIGINAL = "original"
    private const val KEY_DELETED_AT = "deleted_at"
    private const val KEY_TAKEN = "taken"
    private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

    private class Entry(val original: String, val deletedAt: Long, val taken: Long)

    private var index: MutableMap<String, Entry>? = null

    @Synchronized
    private fun index(context: Context): MutableMap<String, Entry> {
        index?.let { return it }
        val map = HashMap<String, Entry>()
        try {
            val json = JSONObject(File(context.filesDir, INDEX_FILE).readText())
            json.keys().forEach { key ->
                val item = json.getJSONObject(key)
                map[key] = Entry(item.getString(KEY_ORIGINAL), item.getLong(KEY_DELETED_AT), item.optLong(KEY_TAKEN))
            }
        } catch (ignored: Exception) {
        }
        index = map
        return map
    }

    @Synchronized
    private fun save(context: Context) {
        val json = JSONObject()
        index(context).forEach { (path, entry) ->
            json.put(
                path,
                JSONObject()
                    .put(KEY_ORIGINAL, entry.original)
                    .put(KEY_DELETED_AT, entry.deletedAt)
                    .put(KEY_TAKEN, entry.taken),
            )
        }
        File(context.filesDir, INDEX_FILE).writeText(json.toString())
    }

    /** Trashed items, newest first. Items whose file vanished are dropped from the index. */
    @Synchronized
    fun list(context: Context): List<Medium> {
        val index = index(context)
        val missing = index.keys.filterNot { File(it).exists() }
        if (missing.isNotEmpty()) {
            missing.forEach(index::remove)
            save(context)
        }

        return index.map { (path, entry) ->
            val file = File(path)
            val name = entry.original.filename
            Medium(
                path = path,
                name = name,
                parentPath = entry.original.parentPath,
                modified = file.lastModified(),
                taken = entry.taken.takeIf { it > 0 } ?: file.lastModified(),
                size = file.length(),
                type = mediaTypeOf(name).takeIf { it != 0 } ?: TYPE_IMAGE,
                deletedAt = entry.deletedAt,
            )
        }.sortedByDescending { it.deletedAt }
    }

    @Synchronized
    fun count(context: Context) = index(context).size

    /** Moves [medium] into the trash and returns its new path, null on failure. */
    @Synchronized
    fun move(context: Context, medium: Medium): String? {
        val source = File(medium.path)
        val folder = File(volumeRoot(context, medium.path), DIR_NAME)
        if (!folder.exists() && !folder.mkdirs()) return null
        File(folder, MediaLibrary.NOMEDIA).let { if (!it.exists()) it.createNewFile() }

        val now = System.currentTimeMillis()
        val target = uniqueFile(File(folder, "${now}_${source.name}"))
        if (!moveFile(source, target)) return null

        index(context)[target.absolutePath] = Entry(medium.path, now, medium.taken)
        save(context)
        return target.absolutePath
    }

    /** Moves trashed items back to where they came from, returning the restored paths. */
    @Synchronized
    fun restore(context: Context, trashPaths: Collection<String>): List<String> {
        val index = index(context)
        val restored = ArrayList<String>()
        trashPaths.forEach { path ->
            val entry = index[path] ?: return@forEach
            val original = File(entry.original)
            original.parentFile?.mkdirs()
            val target = uniqueFile(original)
            if (moveFile(File(path), target)) {
                index.remove(path)
                restored.add(target.absolutePath)
            }
        }
        save(context)
        return restored
    }

    @Synchronized
    fun delete(context: Context, trashPaths: Collection<String>): List<String> {
        val index = index(context)
        val deleted = trashPaths.filter { File(it).delete() || !File(it).exists() }
        deleted.forEach(index::remove)
        save(context)
        return deleted
    }

    @Synchronized
    fun empty(context: Context) {
        delete(context, index(context).keys.toList())
    }

    /** Removes items that have been in the trash for longer than 30 days. */
    @Synchronized
    fun purgeExpired(context: Context) {
        val limit = System.currentTimeMillis() - RETENTION_MS
        val expired = index(context).filterValues { it.deletedAt < limit }.keys
        if (expired.isNotEmpty()) delete(context, expired.toList())
    }

    @Synchronized
    fun originalPath(context: Context, trashPath: String) = index(context)[trashPath]?.original

    private fun volumeRoot(context: Context, path: String): File {
        val file = File(path)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storage = context.getSystemService(StorageManager::class.java)
            storage.getStorageVolume(file)?.directory?.let { return it }
        }

        context.getExternalFilesDirs(null).filterNotNull().forEach { appDir ->
            val root = appDir.absolutePath.substringBefore("/Android/data/")
            if (path.startsWith("$root/")) return File(root)
        }

        @Suppress("DEPRECATION")
        return Environment.getExternalStorageDirectory()
    }
}

/** Renames [source] to [target], copying across file systems when a rename isn't possible. */
fun moveFile(source: File, target: File): Boolean {
    if (source.renameTo(target)) return true
    return try {
        source.copyTo(target)
        target.setLastModified(source.lastModified())
        if (source.delete()) {
            true
        } else {
            target.delete()
            false
        }
    } catch (e: Exception) {
        target.delete()
        false
    }
}
