package com.arslan.gallery.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import java.io.File

/** Reads photos and videos from MediaStore, plus hidden folders straight from disk when enabled. */
class MediaLibrary(context: Context) {

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    /** Every photo and video outside the trash, unsorted. */
    fun loadAll(): List<Medium> {
        val prefs = context.prefs
        val favorites = prefs.favorites
        val excluded = prefs.excludedFolders.map { "$it/" }
        val result = ArrayList<Medium>(4096)

        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false, favorites, result)
        query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true, favorites, result)

        if (prefs.showHidden && hasAllFilesAccess()) {
            addHiddenMedia(favorites, result)
        }

        if (excluded.isEmpty()) return result
        return result.filter { medium -> excluded.none { medium.path.startsWith(it) } }
    }

    private fun query(uri: Uri, isVideo: Boolean, favorites: Set<String>, out: MutableList<Medium>) {
        val projection = buildList {
            add(MediaStore.MediaColumns.DATA)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(COLUMN_DATE_TAKEN)
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (isVideo) add(COLUMN_DURATION)
        }.toTypedArray()

        try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val takenIndex = cursor.getColumnIndexOrThrow(COLUMN_DATE_TAKEN)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val durationIndex = if (isVideo) cursor.getColumnIndexOrThrow(COLUMN_DURATION) else -1

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIndex) ?: continue
                    val name = path.filename
                    // Trashed files can stay in MediaStore under their new path after the rename.
                    if (name.startsWith('.') || path.contains(TRASH_SEGMENT)) continue
                    val type = mediaTypeOf(name, cursor.getString(mimeIndex)).takeIf { it != 0 }
                        ?: if (isVideo) TYPE_VIDEO else TYPE_IMAGE
                    val modified = cursor.getLong(modifiedIndex) * 1000
                    out.add(
                        Medium(
                            path = path,
                            name = name,
                            parentPath = path.parentPath,
                            modified = modified,
                            taken = cursor.getLongOrZero(takenIndex).takeIf { it > 0 } ?: modified,
                            size = cursor.getLong(sizeIndex),
                            type = type,
                            duration = if (durationIndex >= 0) (cursor.getLongOrZero(durationIndex) / 1000).toInt() else 0,
                            isFavorite = path in favorites,
                        )
                    )
                }
            }
        } catch (ignored: Exception) {
        }
    }

    /** Media of folders with a ".nomedia" file and dot-prefixed files of regular folders. */
    private fun addHiddenMedia(favorites: Set<String>, out: MutableList<Medium>) {
        val folders = HashSet<String>()
        folders.addAll(hiddenFolders())
        out.mapTo(folders) { it.parentPath }

        for (folder in folders) {
            if ("$folder/".contains(TRASH_SEGMENT)) continue
            val isHiddenFolder = File(folder, NOMEDIA).exists()
            File(folder).listFiles()?.forEach { file ->
                val name = file.name
                if ((isHiddenFolder || name.startsWith('.')) && file.isFile) {
                    val type = mediaTypeOf(name)
                    if (type != 0) {
                        val modified = file.lastModified()
                        out.add(
                            Medium(
                                path = file.absolutePath,
                                name = name,
                                parentPath = folder,
                                modified = modified,
                                taken = modified,
                                size = file.length(),
                                type = type,
                                isFavorite = file.absolutePath in favorites,
                            )
                        )
                    }
                }
            }
        }
    }

    /** Folders hidden with a ".nomedia" file that contain media. */
    fun hiddenFolders(): List<String> {
        if (!hasAllFilesAccess()) return emptyList()
        val result = ArrayList<String>()
        try {
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns.DATA),
                "${MediaStore.MediaColumns.DATA} LIKE ?",
                arrayOf("%/$NOMEDIA"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val folder = cursor.getString(0)?.parentPath ?: continue
                    if (folder.contains("/${Trash.DIR_NAME}") || folder.contains("/Android/")) continue
                    if (File(folder).list()?.any { it.isMediaFile() } == true) {
                        result.add(folder)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return result
    }

    /**
     * Resolves a content uri handed over by another app to a file path, when MediaStore knows it.
     * [includePending] also matches files a camera is still writing, see [MediaStore.QUERY_ARG_MATCH_PENDING].
     */
    fun resolvePath(uri: Uri, includePending: Boolean = false): ResolvedPath? {
        if (uri.scheme == "file") return uri.path?.let { ResolvedPath(it, isPending = false) }
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME, COLUMN_IS_PENDING)
            val cursor = if (includePending && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE) }
                resolver.query(uri, projection, args, null)
            } else {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            }

            cursor?.use {
                if (!it.moveToFirst()) return null
                val data = it.getString(0)?.takeIf { path -> path.isNotEmpty() } ?: return null
                val pendingIndex = it.getColumnIndex(COLUMN_IS_PENDING)
                val isPending = pendingIndex >= 0 && it.getInt(pendingIndex) == 1
                // Pending files use a temporary ".pending-..." name until they are published.
                val name = if (isPending) it.getString(1) else null
                ResolvedPath(if (name.isNullOrEmpty()) data else "${data.parentPath}/$name", isPending)
            }
        } catch (e: Exception) {
            null
        }
    }

    class ResolvedPath(val path: String, val isPending: Boolean)

    companion object {
        const val NOMEDIA = ".nomedia"
        private const val TRASH_SEGMENT = "/${Trash.DIR_NAME}/"
        private const val COLUMN_DATE_TAKEN = "datetaken"
        private const val COLUMN_DURATION = "duration"
        private const val COLUMN_IS_PENDING = "is_pending"

        /** Builds a medium for a single file that isn't part of the library. */
        fun mediumFromFile(path: String, favorites: Set<String> = emptySet()): Medium? {
            val file = File(path)
            val type = mediaTypeOf(file.name)
            if (!file.exists() || type == 0) return null
            val modified = file.lastModified()
            return Medium(
                path = path,
                name = file.name,
                parentPath = path.parentPath,
                modified = modified,
                taken = modified,
                size = file.length(),
                type = type,
                isFavorite = path in favorites,
            )
        }
    }
}

private fun Cursor.getLongOrZero(index: Int) = if (isNull(index)) 0L else getLong(index)
