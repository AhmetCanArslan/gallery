package com.arslan.gallery.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.io.File

const val TYPE_IMAGE = 1
const val TYPE_VIDEO = 2
const val TYPE_GIF = 4
const val TYPE_RAW = 8
const val TYPE_ALL = TYPE_IMAGE or TYPE_VIDEO or TYPE_GIF or TYPE_RAW

/** Source path of the "all photos" list. */
const val ALL_MEDIA = ""

/** Virtual album holding the favorites. */
const val FAVORITES = "favorites"

/** Virtual album holding trashed items. */
const val TRASH = "trash"

/** Single file handed over by another app that isn't part of the library. */
const val EXTERNAL = "external"

/** Item of a media grid, either a file or the header of a group. */
sealed interface GridItem

@Immutable
data class Section(val title: String) : GridItem

/**
 * A photo or video. [path] is a file path, or a content uri for files handed over by other apps that
 * can't be resolved to a file.
 */
@Immutable
data class Medium(
    val path: String,
    val name: String,
    val parentPath: String,
    /** Last modification, in ms. */
    val modified: Long,
    /** Date taken in ms, the modification date when unknown. */
    val taken: Long,
    val size: Long,
    val type: Int,
    /** Video duration in seconds. */
    val duration: Int = 0,
    val isFavorite: Boolean = false,
    /** When the item was moved into the trash, 0 when it isn't trashed. */
    val deletedAt: Long = 0L,
) : GridItem {
    val isVideo get() = type == TYPE_VIDEO
    val isGif get() = type == TYPE_GIF
    val isRaw get() = type == TYPE_RAW
    val isInTrash get() = deletedAt != 0L
    val isUri get() = path.startsWith("content://")
    val isHidden get() = name.startsWith('.')

    /** Changes whenever the file content does, used as image cache key. */
    val signature get() = "$path:$modified:$size"

    /** Data for image loaders and players. */
    fun model(): Any = if (isUri) Uri.parse(path) else File(path)

    fun uri(): Uri = if (isUri) Uri.parse(path) else Uri.fromFile(File(path))
}

/** Cover the user picked for an album instead of its latest photo. */
sealed interface AlbumCover {
    data class Photo(val path: String) : AlbumCover
    data class Icon(val key: String) : AlbumCover

    fun encode() = when (this) {
        is Photo -> "photo:$path"
        is Icon -> "icon:$key"
    }

    companion object {
        fun parse(value: String): AlbumCover? = when {
            value.startsWith("photo:") -> Photo(value.removePrefix("photo:"))
            value.startsWith("icon:") -> Icon(value.removePrefix("icon:"))
            else -> null
        }
    }
}

@Immutable
data class Album(
    val path: String,
    val name: String,
    val cover: Medium?,
    /** Key of the icon shown instead of [cover], see AlbumIcons. */
    val icon: String? = null,
    val count: Int,
    val modified: Long,
    val taken: Long,
    val size: Long,
    val isHidden: Boolean = false,
) {
    val isFavorites get() = path == FAVORITES
}
